package com.lms.dubbing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
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
import com.lms.dubbing.repository.AiJobRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC18 — điều phối yêu cầu lồng tiếng AI theo đúng chuỗi tiền điều kiện của
 * {@code lms-dubbing-pipeline}: BR-DUB-09 → BR-DUB-07 → BR-DUB-04 → BR-DUB-05 → BR-DUB-06,
 * sau đó tạo {@link AiJob} PENDING và đẩy vào hàng đợi Redis cho AI Worker BRPOP.
 */
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

    @Value("${lms.redis-keys.dubbing-queue}")
    private String queueKey;

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
        if (voiceMappingRepository.findByLanguageAndIsActiveTrue(targetLanguage).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Ngôn ngữ này chưa được kích hoạt lồng tiếng (BR-DUB-07)");
        }

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
