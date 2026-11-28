package com.lms.community.dto;

import java.time.Instant;
import java.util.List;

/** DTO cho bảng điều khiển "Giao tiếp > Hỏi đáp" của Giảng viên (19/09/2026) — xem
 * {@link com.lms.community.service.LessonChatService}. */
public class LessonQaDto {

    public record QuestionRes(
            String id,
            Long lessonId,
            String lessonTitle,
            Long courseId,
            String courseTitle,
            String userName,
            /** (20/09/2026, tính năng mới) — avatar người hỏi. */
            String userAvatarUrl,
            String content,
            Instant createdAt,
            long answerCount,
            boolean hasInstructorAnswer,
            /** (27/11/2026) — id câu trả lời hay nhất, null nếu chưa có. */
            String bestAnswerId,
            /** (27/11/2026) — giảng viên đã mở câu hỏi này (kể từ trả lời mới nhất của người khác). */
            boolean read
    ) {
    }

    public record AnswerRes(
            String id,
            String userName,
            /** (20/09/2026, tính năng mới) — avatar người trả lời. */
            String userAvatarUrl,
            String content,
            Instant createdAt,
            boolean isInstructor,
            /** (27/11/2026) — là câu trả lời hay nhất của câu hỏi. */
            boolean isBest
    ) {
    }

    public record ThreadRes(QuestionRes question, List<AnswerRes> answers) {
    }

    public record ReplyReq(String content) {
    }

    /** (27/11/2026) — đánh dấu câu trả lời hay nhất; {@code answerId} null = bỏ đánh dấu. */
    public record BestAnswerReq(String answerId) {
    }
}
