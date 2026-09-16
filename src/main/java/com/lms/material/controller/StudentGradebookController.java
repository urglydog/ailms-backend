package com.lms.material.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/gradebook")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentGradebookController {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<StudentCourseGradebookRes> getStudentGradebook(Principal principal, @PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        
        // Ensure student is enrolled
        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findByUser_EmailAndCourse_Id(principal.getName(), courseId);
        if (enrollmentOpt.isEmpty()) {
            throw new com.lms.common.exception.AccessDeniedDomainException("Ban chua ghi danh khoa hoc nay");
        }

        // Get all official quizzes (including soft-deleted) for the course
        // Wait, how to get ALL official quizzes?
        // Let's just find all attempts by this user for any quiz in this course.
        List<QuizAttempt> allAttempts = quizAttemptRepository.findByUser_EmailAndQuiz_MaterialGeneration_Course_IdOrderBySubmittedAtDesc(
                principal.getName(), courseId);

        // Group by Quiz
        java.util.Map<Long, List<QuizAttempt>> attemptsByQuiz = allAttempts.stream()
                .collect(Collectors.groupingBy(a -> a.getQuiz().getId()));

        List<QuizGradeDto> quizGrades = new ArrayList<>();

        for (java.util.Map.Entry<Long, List<QuizAttempt>> entry : attemptsByQuiz.entrySet()) {
            List<QuizAttempt> attempts = entry.getValue();
            Quiz quiz = attempts.get(0).getQuiz();
            
            // For each quiz, find highest score, latest score
            BigDecimal maxScore = attempts.stream()
                    .map(QuizAttempt::getScore)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            
            QuizAttempt latestAttempt = attempts.stream()
                    .max(Comparator.comparing(QuizAttempt::getSubmittedAt))
                    .orElse(attempts.get(0));

            quizGrades.add(QuizGradeDto.builder()
                    .quizId(quiz.getId())
                    .quizTitle(quiz.getMaterialGeneration().getTitle())
                    .isOfficial(quiz.getIsOfficial())
                    .isDeleted(quiz.getIsDeleted())
                    .attemptCount(attempts.size())
                    .highestScore(maxScore)
                    .latestScore(latestAttempt.getScore())
                    .latestSubmittedAt(latestAttempt.getSubmittedAt())
                    .latestAttemptId(latestAttempt.getId())
                    .passed(maxScore.compareTo(new BigDecimal("5.00")) >= 0)
                    .attempts(attempts.stream().map(a -> AttemptDto.builder()
                            .id(a.getId())
                            .score(a.getScore())
                            .correctCount(a.getCorrectCount())
                            .totalQuestions(a.getTotalQuestions())
                            .submittedAt(a.getSubmittedAt())
                            .build()).collect(Collectors.toList()))
                    .build());
        }

        StudentCourseGradebookRes res = StudentCourseGradebookRes.builder()
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .quizzes(quizGrades)
                .build();

        return ResponseEntity.ok(res);
    }

    @Data
    @Builder
    public static class StudentCourseGradebookRes {
        private Long courseId;
        private String courseTitle;
        private List<QuizGradeDto> quizzes;
    }

    @Data
    @Builder
    public static class QuizGradeDto {
        private Long quizId;
        private String quizTitle;
        private Boolean isOfficial;
        private Boolean isDeleted;
        private int attemptCount;
        private BigDecimal highestScore;
        private BigDecimal latestScore;
        private LocalDateTime latestSubmittedAt;
        private Long latestAttemptId;
        private boolean passed;
        private List<AttemptDto> attempts;
    }
    
    @Data
    @Builder
    public static class AttemptDto {
        private Long id;
        private BigDecimal score;
        private int correctCount;
        private int totalQuestions;
        private LocalDateTime submittedAt;
    }
}
