package com.lms.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * UC49 nâng cấp (25/09/2026) — Đánh index embedding cho Course để Discovery hiểu ngữ nghĩa thay
 * vì chỉ khớp chữ trên {@code title} (xem {@code CoursePublicService.search}/{@code
 * CourseRepository.searchPublic}). Nhẹ hơn hẳn {@link com.lms.dubbing.service.TranscriptExtractionService}:
 *
 * <ul>
 *   <li>Không cần lock dedupe (Redis {@code setIfAbsent}) — re-embed mỗi lần sửa là RẺ (1 lệnh
 *       gọi Gemini embedContent) và IDEMPOTENT (upsert theo {@code course_id} bên Supabase),
 *       không có khái niệm "đã có rồi thì bỏ qua" như transcript (transcript sinh 1 lần duy nhất).
 *   <li>Không có callback báo kết quả về đây — trạng thái embedding không gate bất kỳ tính năng
 *       user-facing nào (course vẫn tìm được qua bộ lọc category/level/priceType như cũ nếu chưa
 *       kịp embed hoặc embed lỗi, xem nhánh fail-open trong {@code discovery.py}).
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseEmbeddingService {

    private final ChapterRepository chapterRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.course-embedding-queue:lms:course-embedding:jobs}")
    private String queueKey;

    /** Gọi ngay sau khi {@code Course.title}/{@code description} đổi, hoặc tên chương/bài đổi. */
    public void requestEmbedding(Course course) {
        List<Chapter> chapters = chapterRepository.findChaptersWithLessonsByCourseId(course.getId());
        List<String> chapterTitles = chapters.stream().map(Chapter::getTitle).toList();
        List<String> lessonTitles = chapters.stream()
                .flatMap(ch -> ch.getLessons() == null ? Stream.<Lesson>empty() : ch.getLessons().stream())
                .map(Lesson::getTitle)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", course.getId());
        payload.put("title", course.getTitle());
        payload.put("description", course.getDescription());
        payload.put("chapterTitles", chapterTitles);
        payload.put("lessonTitles", lessonTitles);
        try {
            redisTemplate.opsForList().leftPush(queueKey, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Khong tao duoc payload hang doi danh index embedding cho course {}", course.getId(), e);
        }
    }
}
