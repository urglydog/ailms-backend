package com.lms.communication.dto;

import java.time.LocalDateTime;

public class AssignmentDto {

    public record CreateReq(String title, String instructions, LocalDateTime dueDate, Integer maxScore) {
    }

    public record GradeReq(Integer score, String feedback) {
    }

    public record Res(
            Long id,
            Long lessonId,
            String lessonTitle,
            Long courseId,
            String courseTitle,
            String title,
            String instructions,
            LocalDateTime dueDate,
            Integer maxScore,
            LocalDateTime createdAt,
            long submissionCount,
            long gradedCount
    ) {
    }

    public record SubmissionRes(
            Long id,
            Long studentId,
            String studentName,
            String studentEmail,
            String textContent,
            String fileUrl,
            String fileName,
            LocalDateTime submittedAt,
            Integer score,
            String feedback,
            LocalDateTime gradedAt
    ) {
    }

    public record StudentAssignmentRes(Res assignment, SubmissionRes mySubmission) {
    }
}
