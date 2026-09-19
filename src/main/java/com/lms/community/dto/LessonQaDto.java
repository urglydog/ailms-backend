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
            String content,
            Instant createdAt,
            long answerCount,
            boolean hasInstructorAnswer
    ) {
    }

    public record AnswerRes(
            String id,
            String userName,
            String content,
            Instant createdAt,
            boolean isInstructor
    ) {
    }

    public record ThreadRes(QuestionRes question, List<AnswerRes> answers) {
    }

    public record ReplyReq(String content) {
    }
}
