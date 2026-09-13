package com.lms.dubbing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.dto.InternalDubbingDto.SegmentDto;
import com.lms.dubbing.repository.TranscriptRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trích script gốc (WhisperX/Groq ASR, KHÔNG dịch, KHÔNG lồng tiếng) NGAY khi giảng viên nạp
 * xong video — thay vì chỉ chạy như một phần của lần lồng tiếng đầu tiên (UC19 cũ). Lý do: UC24
 * (sinh học liệu) theo BR-MAT-01 phải đọc được transcript gốc bất kể bài học đã lồng tiếng hay
 * chưa; trước đây transcript gốc chỉ có sau khi có người bấm "Lồng tiếng AI" nên sinh học liệu
 * "trước dubbing" là bất khả thi trên thực tế dù đúng đặc tả.
 *
 * <p>Job này CỐ TÌNH đơn giản hơn hẳn {@link DubbingRequestService}/{@code AiJob}: không chunk,
 * không state machine PENDING/PROCESSING/PARTIAL, không AudioTrack — vì không có yêu cầu "phát
 * được ngay chunk đầu" (BR-CHUNK-03) áp dụng ở đây, bài học chưa ai xem lúc vừa upload xong. Nếu
 * job này lỗi hoặc chưa kịp chạy xong, {@link InternalDubbingService#getContext} vẫn tự rơi về
 * hành vi CŨ (tự ASR lại từ đầu khi có người bấm lồng tiếng) — job này chỉ là một tối ưu "chạy
 * trước cho nhanh", không phải điều kiện bắt buộc để các luồng khác hoạt động.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptExtractionService {

    private final LessonRepository lessonRepository;
    private final TranscriptRepository transcriptRepository;
    private final TranscriptPersistenceService transcriptPersistenceService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.transcript-queue:lms:transcript:jobs}")
    private String queueKey;

    @Value("${lms.redis-keys.transcript-lock-prefix:lock:transcript:}")
    private String lockPrefix;

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /** Gọi ngay sau khi {@code Lesson.videoUrl}/{@code videoSource} được lưu (UC34). */
    public void requestExtraction(Lesson lesson) {
        if (transcriptRepository.findByLesson_IdAndIsSourceTrue(lesson.getId()).isPresent()) {
            return; // Đã có transcript gốc từ trước (vd nạp lại video sau khi xoá) — không cần chạy lại.
        }
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockPrefix + lesson.getId(), "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return; // Đã có job trích script đang chạy cho đúng bài này.
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lessonId", lesson.getId());
        payload.put("videoSource", lesson.getVideoSource());
        payload.put("videoUrl", lesson.getVideoUrl());
        payload.put("durationSec", lesson.getDurationSec());
        try {
            redisTemplate.opsForList().leftPush(queueKey, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            redisTemplate.delete(lockPrefix + lesson.getId());
            log.error("Khong tao duoc payload hang doi trich script goc cho lesson {}", lesson.getId(), e);
        }
    }

    /** Callback từ AI Worker — xem {@code InternalTranscriptController}. */
    @Transactional
    public void reportResult(Long lessonId, String outcome, String detectedLanguage,
                              java.util.List<SegmentDto> segments, String errorMessage) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        redisTemplate.delete(lockPrefix + lessonId);

        if ("COMPLETED".equals(outcome) && segments != null && !segments.isEmpty()) {
            transcriptPersistenceService.saveSourceSegments(lesson, detectedLanguage, segments);
            log.info("Da trich xong script goc cho lesson {} ({} cau, ngon ngu {})",
                    lessonId, segments.size(), detectedLanguage);
        } else if ("SKIPPED".equals(outcome)) {
            log.info("Bo qua trich script goc cho lesson {} (video khong co loi thoai dang ke): {}",
                    lessonId, errorMessage);
        } else {
            // Khong doi trang thai Lesson/thong bao gi them — day chi la buoc toi uu "chay truoc
            // cho nhanh", that bai o day khong chan hoc vien/giang vien lam gi ca (xem docblock
            // lop nay). Giang vien co the tu kich hoat lai bang cach xoa + nap lai video, hoac
            // pipeline long tieng se tu ASR lai khi co ai bam "Long tieng AI".
            log.warn("Trich script goc that bai cho lesson {}: {}", lessonId, errorMessage);
        }
    }
}
