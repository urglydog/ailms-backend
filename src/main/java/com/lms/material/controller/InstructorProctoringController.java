package com.lms.material.controller;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.entity.QuizAttemptViolation;
import com.lms.material.repository.ProctoringRecordingRepository;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizAttemptViolationRepository;
import com.lms.material.repository.QuizRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * UC-ANTICHEAT (25/09/2026) — màn hình "Giám sát thi" cho giảng viên: danh sách quiz có bật
 * giám sát → danh sách lượt thi kèm risk level → chi tiết 1 lượt thi (video bằng chứng + marker
 * vi phạm). KHÔNG mở rộng {@code InstructorGradebookController} — Gradebook chỉ có điểm số tổng
 * hợp (chỉ lượt gần nhất/cao nhất mỗi học viên), không hợp để nhét thêm video/timeline. Logic đặt
 * thẳng trong controller, đúng phong cách {@code InstructorGradebookController} (inner DTO
 * {@code @Data @Builder}) thay vì tách service riêng.
 */
@RestController
@RequestMapping("/api/v1/instructor/proctoring")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class InstructorProctoringController {

    private final CourseRepository courseRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptViolationRepository quizAttemptViolationRepository;
    private final ProctoringRecordingRepository proctoringRecordingRepository;

    @GetMapping("/courses/{courseId}/quizzes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<QuizSummaryDto>> getProctoredQuizzes(Principal principal, @PathVariable Long courseId) {
        Course course = loadOwnedCourse(courseId, principal.getName());
        List<Quiz> quizzes = quizRepository.findByMaterialGeneration_Course_IdAndIsProctoredTrue(course.getId());

        return ResponseEntity.ok(quizzes.stream().map(q -> {
            List<QuizAttempt> attempts = quizAttemptRepository.findByQuiz_IdAndStatusOrderBySubmittedAtDesc(q.getId(), "COMPLETED");
            long highRiskCount = quizAttemptRepository.countByQuiz_IdAndAiRiskLevel(q.getId(), "HIGH");
            return QuizSummaryDto.builder()
                    .quizId(q.getId())
                    .title(q.getMaterialGeneration().getTitle())
                    .quizType(q.getQuizType().name())
                    .attemptCount(attempts.size())
                    .highRiskCount(highRiskCount)
                    .build();
        }).toList());
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AttemptSummaryDto>> getAttempts(Principal principal, @PathVariable Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        loadOwnedCourse(quiz.getMaterialGeneration().getCourse().getId(), principal.getName());

        List<QuizAttempt> attempts = quizAttemptRepository.findByQuiz_IdAndStatusOrderBySubmittedAtDesc(quizId, "COMPLETED");
        return ResponseEntity.ok(attempts.stream().map(a -> AttemptSummaryDto.builder()
                        .attemptId(a.getId())
                        .studentName(a.getUser().getFullName())
                        .studentEmail(a.getUser().getEmail())
                        .submittedAt(a.getSubmittedAt())
                        .violationCount(a.getViolationCount())
                        .aiRiskLevel(a.getAiRiskLevel())
                        .hasRecording(proctoringRecordingRepository.findByAttempt_Id(a.getId()).isPresent())
                        .build())
                .toList());
    }

    @GetMapping("/attempts/{attemptId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AttemptDetailDto> getAttemptDetail(Principal principal, @PathVariable Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        loadOwnedCourse(attempt.getQuiz().getMaterialGeneration().getCourse().getId(), principal.getName());

        var recording = proctoringRecordingRepository.findByAttempt_Id(attemptId).orElse(null);
        List<QuizAttemptViolation> violations = quizAttemptViolationRepository.findByAttempt_IdOrderByCreatedAtAsc(attemptId);
        LocalDateTime startedAt = attempt.getCreatedAt();

        List<ViolationMarkerDto> markers = violations.stream().map(v -> ViolationMarkerDto.builder()
                        .type(v.getType())
                        .detail(v.getDetail())
                        .offsetSec(startedAt != null ? Duration.between(startedAt, v.getCreatedAt()).getSeconds() : 0)
                        .build())
                .toList();

        return ResponseEntity.ok(AttemptDetailDto.builder()
                .attemptId(attempt.getId())
                .studentName(attempt.getUser().getFullName())
                .studentEmail(attempt.getUser().getEmail())
                .submittedAt(attempt.getSubmittedAt())
                .violationCount(attempt.getViolationCount())
                .aiRiskLevel(attempt.getAiRiskLevel())
                .aiRiskExplanation(attempt.getAiRiskExplanation())
                .videoUrl(recording != null ? recording.getVideoUrl() : null)
                .durationSec(recording != null ? recording.getDurationSec() : null)
                .violations(markers)
                .build());
    }

    private Course loadOwnedCourse(Long courseId, String instructorEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Chỉ giảng viên sở hữu khóa học mới xem được màn hình giám sát thi");
        }
        return course;
    }

    @Data
    @Builder
    public static class QuizSummaryDto {
        private Long quizId;
        private String title;
        private String quizType;
        private int attemptCount;
        private long highRiskCount;
    }

    @Data
    @Builder
    public static class AttemptSummaryDto {
        private Long attemptId;
        private String studentName;
        private String studentEmail;
        private LocalDateTime submittedAt;
        private Integer violationCount;
        private String aiRiskLevel;
        private boolean hasRecording;
    }

    @Data
    @Builder
    public static class ViolationMarkerDto {
        private String type;
        private String detail;
        private long offsetSec;
    }

    @Data
    @Builder
    public static class AttemptDetailDto {
        private Long attemptId;
        private String studentName;
        private String studentEmail;
        private LocalDateTime submittedAt;
        private Integer violationCount;
        private String aiRiskLevel;
        private String aiRiskExplanation;
        private String videoUrl;
        private Integer durationSec;
        private List<ViolationMarkerDto> violations;
    }
}
