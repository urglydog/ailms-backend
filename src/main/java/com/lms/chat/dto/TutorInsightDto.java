package com.lms.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Insight từ Gia sư AI cho giảng viên (26/11/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p><b>Riêng tư:</b> không bao giờ chứa tên học viên hay câu hỏi nguyên văn — chủ đề chỉ là
 * các TỪ KHOÁ chung xuất hiện ở câu hỏi của ít nhất 2 học viên khác nhau.
 */
public class TutorInsightDto {

    /**
     * @param label         nhãn chủ đề (từ khoá chung, không phải câu nguyên văn)
     * @param learnerCount  số học viên khác nhau (luôn &gt;= 2)
     * @param questionCount số câu hỏi trong nhóm
     */
    public record TopicRes(String label, int learnerCount, int questionCount) {}

    /**
     * @param unusual   bài có số câu hỏi bất thường (nhiều hơn hẳn trung bình khoá)
     * @param poorCount số câu Gia sư trả lời chưa tốt (hỏi lặp / ngoài nội dung bài)
     */
    public record LessonInsightRes(Long lessonId, String lessonTitle, int questionCount, int learnerCount,
                                   boolean unusual, int poorCount, List<TopicRes> topics) {}

    /** @param reason REPEAT (hỏi lại nhiều lần) | NOT_COVERED (Gia sư báo ngoài nội dung bài) */
    public record PoorTopicRes(Long lessonId, String lessonTitle, String label, String reason, int learnerCount) {}

    /** @param status NONE | PENDING | READY | FAILED */
    public record SummaryRes(String status, String text, List<String> actions, LocalDateTime generatedAt) {}

    public record CourseInsightRes(Long courseId, int windowDays, int totalQuestions, int learnerCount,
                                   List<LessonInsightRes> lessons, List<PoorTopicRes> poorTopics,
                                   SummaryRes summary) {}
}
