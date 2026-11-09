package com.lms.dubbing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.enums.JobStatus;
import com.lms.common.enums.TrackStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.dto.DubbingDto.RequestReq;
import com.lms.dubbing.dto.DubbingDto.Res;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.entity.AudioTrack;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AiJobRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * UC18 — điều phối yêu cầu lồng tiếng AI theo đúng chuỗi tiền điều kiện của
 * {@code lms-dubbing-pipeline}: BR-DUB-09 → BR-DUB-07 → BR-DUB-04 → BR-DUB-05 → BR-DUB-06,
 * sau đó tạo {@link AiJob} PENDING và đẩy vào hàng đợi Redis cho AI Worker BRPOP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DubbingRequestService {

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VoiceMappingRepository voiceMappingRepository;
    private final AudioTrackRepository audioTrackRepository;
    private final AiJobRepository aiJobRepository;
    private final DubbingLockService dubbingLockService;
    private final DubbingQuotaService dubbingQuotaService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    @Value("${lms.redis-keys.dubbing-queue}")
    private String queueKey;

    @Value("${lms.redis-keys.dubbing-progress}")
    private String progressChannel;

    @Value("${lms.internal.api-token}")
    private String internalApiToken;

    @Transactional
    public Res requestDubbing(String email, Long lessonId, RequestReq req) {
        String targetLanguage = req.targetLanguage();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        requireLessonAccess(user, lesson);

        // ① BR-DUB-09 — ngôn ngữ đích phải khác ngôn ngữ gốc.
        if (targetLanguage.equalsIgnoreCase(lesson.getSourceLanguage())) {
            throw new BusinessRuleViolationException(
                    "Ngôn ngữ lồng tiếng phải khác ngôn ngữ gốc của bài học (BR-DUB-09)");
        }

        // ② BR-DUB-07 — ngôn ngữ phải nằm trong voice_mappings đang active.
        List<VoiceMapping> activeVoices = voiceMappingRepository.findByLanguageAndIsActiveTrue(targetLanguage);
        if (activeVoices.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Ngôn ngữ này chưa được kích hoạt lồng tiếng (BR-DUB-07)");
        }
        // UC20 mở rộng — chọn giọng đọc là tuỳ chọn, nhưng nếu CÓ gửi lên thì phải là giọng
        // thật đang active của đúng ngôn ngữ này (không tin dữ liệu từ client mù quáng).
        VoiceMapping requestedVoice = resolveRequestedVoice(activeVoices, req.voiceName());

        // ③ BR-DUB-04 — đã có bản lồng tiếng hoàn chỉnh thì phát luôn, không chạy lại pipeline.
        Optional<AudioTrack> existingTrack = audioTrackRepository.findByLesson_IdAndLanguage(lessonId, targetLanguage);
        if (existingTrack.isPresent() && existingTrack.get().getStatus() == TrackStatus.COMPLETED) {
            return new Res("AVAILABLE", null, existingTrack.get().getFinalUrl());
        }

        // ④ BR-DUB-05 — đã có job đang chạy thì chỉ subscribe, không tạo job mới (không tính hạn ngạch).
        Optional<AiJob> existingJob = aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(lessonId, targetLanguage, 1);
        if (existingJob.isPresent()) {
            return new Res("PROCESSING", existingJob.get().getId(), null);
        }

        // ⑤ BR-DUB-06 — hạn ngạch job/ngày. Chỉ tính từ đây, sau khi đã loại các trường hợp dedupe.
        dubbingQuotaService.consume(user.getId(), user.getRole());

        // ⑥ Redis SETNX — lớp khóa thứ 2 chống race giữa nhiều instance backend.
        if (!dubbingLockService.tryLock(lessonId, targetLanguage)) {
            return existingJobOrThrow(lessonId, targetLanguage);
        }

        try {
            AiJob job = new AiJob();
            job.setLesson(lesson);
            job.setTargetLanguage(targetLanguage);
            job.setRequestedBy(user);
            job.setVoiceMapping(requestedVoice);
            AiJob saved = aiJobRepository.save(job);

            // ⑦ LPUSH cho AI Worker BRPOP.
            redisTemplate.opsForList().leftPush(queueKey, toJson(saved, lesson, targetLanguage));

            return new Res("CREATED", saved.getId(), null);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE (lesson_id, target_language, active_flag) ở DB — lớp phòng thủ cuối nếu
            // khóa Redis vừa hết hạn/bị mất đúng lúc race xảy ra.
            dubbingLockService.release(lessonId, targetLanguage);
            return existingJobOrThrow(lessonId, targetLanguage);
        } catch (RuntimeException e) {
            dubbingLockService.release(lessonId, targetLanguage);
            throw e;
        }
    }

    /**
     * UC45 — Admin bấm "Thử lại" cho job {@code FAILED}. Tái sử dụng NGUYÊN {@code AiJob.id}
     * cũ (không tạo job mới) — phía AI Worker chỉ nhận payload y hệt lúc tạo mới, không cần
     * biết đây là lần retry. {@code SKIPPED} tuyệt đối không được retry (BR-DUB-10).
     */
    @Transactional
    public Res retryJob(Long jobId) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("AiJob", jobId));

        if (job.getStatus() != JobStatus.FAILED) {
            throw new BusinessRuleViolationException(
                    "Chỉ retry được job đang ở trạng thái FAILED (hiện tại: " + job.getStatus() + ")");
        }
        if (!job.canRetry()) {
            throw new BusinessRuleViolationException("Job đã hết lượt retry (tối đa 3 lần) — BR-CHUNK-04");
        }

        Long lessonId = job.getLesson().getId();
        String targetLanguage = job.getTargetLanguage();

        Optional<AiJob> activeJob = aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(lessonId, targetLanguage, 1);
        if (activeJob.isPresent() && !activeJob.get().getId().equals(jobId)) {
            throw new ConflictException("Đã có yêu cầu lồng tiếng khác đang xử lý cho bài học và ngôn ngữ này");
        }
        if (!dubbingLockService.tryLock(lessonId, targetLanguage)) {
            throw new ConflictException("Không khóa lại được tiến trình lồng tiếng, vui lòng thử lại sau");
        }

        try {
            job.setStatus(JobStatus.PENDING);
            job.setActiveFlag(1);
            job.setDoneChunks(0);
            job.setErrorMessage(null);
            job.setStartedAt(null);
            job.setFinishedAt(null);
            job.setRetryCount(job.getRetryCount() + 1);
            AiJob saved = aiJobRepository.save(job);

            redisTemplate.opsForList().leftPush(queueKey, toJson(saved, job.getLesson(), targetLanguage));

            return new Res("CREATED", saved.getId(), null);
        } catch (RuntimeException e) {
            dubbingLockService.release(lessonId, targetLanguage);
            throw e;
        }
    }

    /**
     * UC20 — học viên chủ động huỷ job đang chạy giữa chừng. Đây là nơi DUY NHẤT chuyển
     * trạng thái CANCELLED — DB ({@code be/}) luôn là nguồn sự thật, cập nhật NGAY trong
     * transaction này, KHÔNG chờ AI Worker phản hồi rồi mới coi là huỷ xong (Celery revoke
     * chỉ là hành động "cố dừng tiến trình đang chạy thật sớm nhất có thể" — best-effort,
     * lỗi ở bước đó không được làm hỏng trải nghiệm huỷ của học viên).
     *
     * <p>Dữ liệu ĐÃ lưu của các chunk hoàn tất trước đó (BR-CHUNK-03) được GIỮ NGUYÊN,
     * không xoá — mỗi chunk đã COMPLETED là file audio hợp lệ, độc lập, học viên vẫn nghe
     * được đoạn đó bình thường. Chunk đang xử lý dở khi bị huỷ CHƯA từng được lưu (transcript/
     * audio chỉ ghi khi cả chunk xong, xem {@code InternalDubbingService.upsertChunk}) nên
     * không có gì để xoá ở đó cả.
     */
    @Transactional
    public Res cancelDubbing(String email, Long lessonId, String targetLanguage) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        requireLessonAccess(user, lesson);

        AiJob job = aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(lessonId, targetLanguage, 1)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Không có job lồng tiếng nào đang chạy cho ngôn ngữ này"));

        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.PROCESSING) {
            throw new ConflictException("Job đã kết thúc (" + job.getStatus() + "), không huỷ được nữa");
        }

        job.setStatus(JobStatus.CANCELLED);
        job.releaseActiveFlag();
        job.setErrorMessage("Bị huỷ thủ công bởi học viên");
        job.setFinishedAt(LocalDateTime.now());
        aiJobRepository.save(job);

        dubbingLockService.release(lessonId, targetLanguage);
        publishCancelledEvent(job.getId(), lessonId);
        requestAiWorkerCancel(job.getId(), job.getCeleryTaskId());

        return new Res("CANCELLED", job.getId(), null);
    }

    /**
     * Báo NGAY qua WS để FE dừng progress bar không phải chờ vòng poll/callback nào —
     * publish thẳng lên CÙNG kênh Redis Pub/Sub mà AI Worker dùng
     * ({@code app/redis_client.py::publish_progress}); {@link com.lms.dubbing.messaging.DubbingProgressSubscriber}
     * đã lắng nghe kênh này rồi nên không cần thêm code phía subscriber.
     */
    private void publishCancelledEvent(Long jobId, Long lessonId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("lessonId", lessonId);
        payload.put("status", "CANCELLED");
        try {
            redisTemplate.convertAndSend(progressChannel, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Khong tao duoc payload huy job {} de publish qua WS", jobId, e);
        }
    }

    /**
     * Cố dừng tiến trình Celery đang chạy thật (nếu đã start) — best-effort, KHÔNG throw:
     * DB đã ghi CANCELLED ở trên rồi, học viên không cần biết bước này thành công hay không.
     * {@code celeryTaskId} null nghĩa là job còn PENDING, chưa từng được AI Worker nhận —
     * không có gì để huỷ phía đó.
     */
    private void requestAiWorkerCancel(Long jobId, String celeryTaskId) {
        if (celeryTaskId == null) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("celeryTaskId", celeryTaskId), headers);
        try {
            restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/admin/dubbing-jobs/" + jobId + "/cancel", entity, Void.class);
        } catch (RestClientException e) {
            log.warn("Khong bao duoc AI Worker dung task {} cho job {} (khong anh huong ket qua huy o DB): {}",
                    celeryTaskId, jobId, e.getMessage());
        }
    }

    /** UC20 mở rộng — {@code null}/rỗng nghĩa là học viên không chọn (giữ hành vi cũ, AI Worker tự
     * lấy giọng {@code isDefault}); có gửi lên thì PHẢI khớp đúng 1 giọng đang active của ngôn ngữ
     * này, không thì coi là yêu cầu sai (client gửi tên giọng không tồn tại/đã bị Admin tắt). */
    private VoiceMapping resolveRequestedVoice(List<VoiceMapping> activeVoices, String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return null;
        }
        return activeVoices.stream()
                .filter(v -> voiceName.equals(v.getVoiceName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Giọng đọc \"" + voiceName + "\" không khả dụng cho ngôn ngữ này"));
    }

    /** Học viên đã ghi danh HOẶC giảng viên sở hữu khóa học (pre-warm, BR-DUB-06) mới được yêu cầu. */
    private void requireLessonAccess(User user, Lesson lesson) {
        Course course = lesson.getChapter().getCourse();
        boolean isOwningInstructor = course.getInstructor().getId().equals(user.getId());
        if (isOwningInstructor) {
            return;
        }
        boolean enrolled = enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId());
        if (!enrolled) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }
    }

    private Res existingJobOrThrow(Long lessonId, String targetLanguage) {
        AiJob raceJob = aiJobRepository.findByLesson_IdAndTargetLanguageAndActiveFlag(lessonId, targetLanguage, 1)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Đã có yêu cầu lồng tiếng khác đang xử lý, vui lòng thử lại sau"));
        return new Res("PROCESSING", raceJob.getId(), null);
    }

    private String toJson(AiJob job, Lesson lesson, String targetLanguage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.getId());
        payload.put("lessonId", lesson.getId());
        payload.put("videoUrl", lesson.getVideoUrl());
        payload.put("targetLanguage", targetLanguage);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được payload hàng đợi lồng tiếng", e);
        }
    }
}
