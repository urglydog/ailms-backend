package com.lms.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorInsightDto.CourseInsightRes;
import com.lms.chat.dto.TutorInsightDto.LessonInsightRes;
import com.lms.chat.dto.TutorInsightDto.SummaryRes;
import com.lms.chat.entity.CourseTutorInsight;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.chat.repository.CourseTutorInsightRepository;
import com.lms.chat.service.TutorInsightAnalyzer.Question;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Insight từ Gia sư AI cho giảng viên (26/11/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p>Số liệu gộp (chủ đề, bài nhiều câu hỏi, câu trả lời chưa tốt) tính trực tiếp mỗi lần xem
 * bằng {@link TutorInsightAnalyzer}. Riêng phần <b>tóm tắt + đề xuất do AI viết</b> được cache ở
 * {@code course_tutor_insights}: job hằng ngày (02:30) xếp hàng các khoá có câu hỏi mới trong 24h,
 * giảng viên cũng bấm "Làm mới" được (giới hạn {@link #REFRESH_COOLDOWN_MINUTES} phút/lần); job
 * nền {@link #processPending} mới gọi AI Worker — request không bao giờ chờ LLM. Chỉ số liệu
 * ĐÃ GỘP (từ khoá chủ đề &gt;= 2 học viên, số đếm) được gửi cho LLM, không câu hỏi nguyên văn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorInsightService {

    static final int WINDOW_DAYS = 90;
    static final int SCAN_LIMIT = 5000;
    static final int REFRESH_COOLDOWN_MINUTES = 10;
    /** Hiệu năng (28/11/2026): số liệu gộp (quét tới {@link #SCAN_LIMIT} câu hỏi + phân tích từ khoá)
     * được dùng lại trong 60s cho mỗi khoá — trang insight không chạy lại phép quét mỗi lần tải/đổi bài. */
    static final long COMPUTE_TTL_MS = 60_000;
    private record Computed(long expiresAt, TutorInsightAnalyzer.Result result) {}
    private final Map<Long, Computed> computeCache = new ConcurrentHashMap<>();

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CourseTutorInsightRepository insightRepository;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CourseInsightRes insights(String email, Long courseId, Long lessonId) {
        Course course = loadOwned(email, courseId);
        TutorInsightAnalyzer.Result r = cachedCompute(course.getId());
        List<LessonInsightRes> lessons = lessonId == null ? r.lessons()
                : r.lessons().stream().filter(l -> l.lessonId().equals(lessonId)).toList();
        var poor = lessonId == null ? r.poorTopics()
                : r.poorTopics().stream().filter(p -> p.lessonId().equals(lessonId)).toList();
        return new CourseInsightRes(courseId, WINDOW_DAYS, r.totalQuestions(), r.learnerCount(), lessons, poor,
                summary(courseId));
    }

    /** Xếp hàng sinh lại tóm tắt AI (có giới hạn tần suất). Trả trạng thái hiện tại. */
    @Transactional
    public SummaryRes requestSummary(String email, Long courseId) {
        loadOwned(email, courseId);
        CourseTutorInsight row = insightRepository.findByCourseId(courseId).orElseGet(() -> {
            CourseTutorInsight n = new CourseTutorInsight();
            n.setCourseId(courseId);
            n.setStatus(CourseTutorInsight.FAILED); // để nhánh dưới chuyển sang PENDING
            return n;
        });
        LocalDateTime retryAfter = retryAfter(row);
        boolean cooling = retryAfter != null;
        if (!CourseTutorInsight.PENDING.equals(row.getStatus()) && !cooling) {
            row.setStatus(CourseTutorInsight.PENDING);
            insightRepository.save(row);
        }
        return toSummary(row);
    }

    /** Hằng ngày 02:30 — xếp hàng các khoá có câu hỏi mới trong 24h. */
    @Scheduled(cron = "0 30 2 * * ?")
    @SchedulerLock(name = "TutorInsightService.queueDaily", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void queueDaily() {
        for (Long courseId : chatMessageRepository.findCourseIdsWithQuestionsSince(LocalDateTime.now().minusDays(1))) {
            CourseTutorInsight row = insightRepository.findByCourseId(courseId).orElseGet(() -> {
                CourseTutorInsight n = new CourseTutorInsight();
                n.setCourseId(courseId);
                return n;
            });
            row.setStatus(CourseTutorInsight.PENDING);
            insightRepository.save(row);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 90_000)
    @SchedulerLock(name = "TutorInsightService.processPending", lockAtMostFor = "PT10M", lockAtLeastFor = "PT24S")
    @Transactional
    public void processPending() {
        for (CourseTutorInsight row : insightRepository.findByStatusOrderByUpdatedAtAsc(
                CourseTutorInsight.PENDING, PageRequest.of(0, 3))) {
            generate(row);
        }
    }

    void generate(CourseTutorInsight row) {
        Course course = courseRepository.findById(row.getCourseId()).orElse(null);
        if (course == null) {
            insightRepository.delete(row);
            return;
        }
        try {
            TutorInsightAnalyzer.Result r = compute(course.getId());
            if (r.totalQuestions() == 0) {
                row.setSummary(null);
                row.setActions(null);
            } else {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("course_title", course.getTitle());
                payload.put("window_days", WINDOW_DAYS);
                payload.put("total_questions", r.totalQuestions());
                payload.put("learner_count", r.learnerCount());
                payload.put("lessons", r.lessons().stream().map(l -> Map.of(
                        "lesson_title", l.lessonTitle(),
                        "question_count", l.questionCount(),
                        "unusual", l.unusual(),
                        "poor_count", l.poorCount(),
                        "topics", l.topics().stream().map(t -> Map.of(
                                "label", t.label(), "learner_count", t.learnerCount())).toList()))
                        .toList());
                payload.put("poor_topics", r.poorTopics().stream().map(p -> Map.of(
                        "lesson_title", p.lessonTitle(), "label", p.label(), "reason", p.reason(),
                        "learner_count", p.learnerCount())).toList());
                AiWorkerInsightRes res = restTemplate.postForObject(
                        aiWorkerConfig.getBaseUrl() + "/api/v1/tutor/insight-summary", payload, AiWorkerInsightRes.class);
                if (res == null || res.summary() == null || res.summary().isBlank()) {
                    throw new IllegalStateException("AI Worker trả tóm tắt rỗng");
                }
                row.setSummary(res.summary().trim());
                row.setActions(objectMapper.writeValueAsString(
                        res.actions() == null ? List.of() : res.actions().stream().limit(5).toList()));
            }
            row.setStatus(CourseTutorInsight.READY);
            row.setGeneratedAt(LocalDateTime.now());
        } catch (Exception exc) {
            log.warn("Sinh tóm tắt Insight AI thất bại cho khoá {}: {}", row.getCourseId(), exc.getMessage());
            row.setStatus(CourseTutorInsight.FAILED);
        }
        insightRepository.save(row);
    }

    private TutorInsightAnalyzer.Result cachedCompute(Long courseId) {
        long now = System.currentTimeMillis();
        Computed c = computeCache.get(courseId);
        if (c != null && c.expiresAt() > now) return c.result();
        TutorInsightAnalyzer.Result r = compute(courseId);
        if (computeCache.size() >= 1_000) computeCache.clear();
        computeCache.put(courseId, new Computed(now + COMPUTE_TTL_MS, r));
        return r;
    }

    TutorInsightAnalyzer.Result compute(Long courseId) {
        Map<Long, String> lessons = new LinkedHashMap<>();
        for (Lesson l : lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(courseId)) {
            lessons.put(l.getId(), l.getTitle());
        }
        List<Question> qs = new ArrayList<>();
        for (Object[] row : chatMessageRepository.findQuestionsForCourseInsight(
                courseId, LocalDateTime.now().minusDays(WINDOW_DAYS), SCAN_LIMIT)) {
            qs.add(new Question(
                    row[0] == null ? "" : row[0].toString(),
                    row[1] == null ? null : ((Number) row[1]).longValue(),
                    row[2] == null ? null : ((Number) row[2]).longValue(),
                    row[3] == null ? null : row[3].toString(),
                    toDateTime(row[4])));
        }
        return TutorInsightAnalyzer.analyze(qs, lessons);
    }

    private SummaryRes summary(Long courseId) {
        return insightRepository.findByCourseId(courseId).map(this::toSummary)
                .orElse(new SummaryRes("NONE", null, List.of(), null, null));
    }

    private SummaryRes toSummary(CourseTutorInsight row) {
        List<String> actions = List.of();
        if (row.getActions() != null) {
            try {
                actions = objectMapper.readValue(row.getActions(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // giữ rỗng
            }
        }
        return new SummaryRes(row.getStatus(), row.getSummary(), actions, row.getGeneratedAt(),
                CourseTutorInsight.PENDING.equals(row.getStatus()) ? null : retryAfter(row));
    }

    /**
     * Giới hạn {@link #REFRESH_COOLDOWN_MINUTES} phút/lần tính từ LẦN THỬ gần nhất (27/11/2026, sửa lỗi):
     * trước đây chỉ tính từ {@code generatedAt} (chỉ đặt khi THÀNH CÔNG) nên khi AI Worker lỗi, bấm
     * "Thử lại" liên tục gọi AI Worker không giới hạn. Lần thử thất bại lấy mốc {@code updatedAt}.
     * Trả null nếu được thử ngay.
     */
    static LocalDateTime retryAfter(CourseTutorInsight row) {
        LocalDateTime last = row.getGeneratedAt();
        if (CourseTutorInsight.FAILED.equals(row.getStatus()) && row.getUpdatedAt() != null
                && (last == null || row.getUpdatedAt().isAfter(last))) {
            last = row.getUpdatedAt();
        }
        if (last == null) return null;
        LocalDateTime next = last.plusMinutes(REFRESH_COOLDOWN_MINUTES);
        return next.isAfter(LocalDateTime.now()) ? next : null;
    }

    private Course loadOwned(String email, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (course.getInstructor() == null || !email.equals(course.getInstructor().getEmail())) {
            throw new AccessDeniedDomainException("Bạn không có quyền xem Insight của khóa học này");
        }
        return course;
    }

    private static LocalDateTime toDateTime(Object o) {
        if (o instanceof LocalDateTime t) return t;
        if (o instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    /** Khớp response {@code POST /api/v1/tutor/insight-summary} của AI Worker. */
    record AiWorkerInsightRes(String summary, List<String> actions) {}
}
