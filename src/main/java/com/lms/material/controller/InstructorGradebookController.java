package com.lms.material.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizAnswer;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.entity.QuizOption;
import com.lms.material.repository.QuizAnswerRepository;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizOptionRepository;
import com.lms.material.repository.QuizRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/instructor/courses/{courseId}/gradebook")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorGradebookController {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<GradebookRes> getGradebook(Principal principal, @PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Chỉ giảng viên sở hữu khóa học mới xem được bảng điểm");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);
        Optional<Quiz> officialQuizOpt = quizRepository.findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(courseId);

        List<StudentGradeDto> studentGrades = new ArrayList<>();
        int totalAttemptsCount = 0;
        BigDecimal sumHighestScore = BigDecimal.ZERO;
        int passedStudentCount = 0;

        if (officialQuizOpt.isPresent()) {
            Quiz quiz = officialQuizOpt.get();
            for (Enrollment enrollment : enrollments) {
                User student = enrollment.getUser();
                List<QuizAttempt> attempts = quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(student.getEmail(), quiz.getId());
                
                int attemptsCount = attempts.size();
                totalAttemptsCount += attemptsCount;

                BigDecimal maxScore = BigDecimal.ZERO;
                BigDecimal latestScore = BigDecimal.ZERO;
                LocalDateTime latestSubmittedAt = null;

                if (!attempts.isEmpty()) {
                    maxScore = attempts.get(0).getScore(); // First attempt in descending order by score
                    
                    // Find latest attempt by submittedAt
                    QuizAttempt latestAttempt = attempts.stream()
                            .max(Comparator.comparing(QuizAttempt::getSubmittedAt))
                            .orElse(attempts.get(0));
                    latestScore = latestAttempt.getScore();
                    latestSubmittedAt = latestAttempt.getSubmittedAt();

                    sumHighestScore = sumHighestScore.add(maxScore);
                    if (maxScore.compareTo(new BigDecimal("5.00")) >= 0) {
                        passedStudentCount++;
                    }
                }

                studentGrades.add(StudentGradeDto.builder()
                        .userId(student.getId())
                        .fullName(student.getFullName() != null ? student.getFullName() : student.getEmail())
                        .email(student.getEmail())
                        .attemptCount(attemptsCount)
                        .highestScore(maxScore)
                        .latestScore(latestScore)
                        .latestSubmittedAt(latestSubmittedAt)
                        .passed(maxScore.compareTo(new BigDecimal("5.00")) >= 0)
                        .latestAttemptId(!attempts.isEmpty() ? attempts.get(0).getId() : null)
                        .build());
            }
        }

        int totalStudents = enrollments.size();
        BigDecimal avgScore = totalStudents > 0 ? 
                sumHighestScore.divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double passRate = totalStudents > 0 ? 
                (double) passedStudentCount / totalStudents * 100.0 : 0.0;

        GradebookRes res = GradebookRes.builder()
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .totalStudents(totalStudents)
                .totalAttempts(totalAttemptsCount)
                .averageScore(avgScore)
                .passRatePercentage(BigDecimal.valueOf(passRate).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .hasOfficialQuiz(officialQuizOpt.isPresent())
                .students(studentGrades)
                .build();

        return ResponseEntity.ok(res);
    }

    @GetMapping("/attempts/{attemptId}/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<AttemptInspectionRes> getAttemptDetail(Principal principal, @PathVariable Long courseId, @PathVariable Long attemptId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Chỉ giảng viên sở hữu khóa học mới xem được bài làm của học viên");
        }

        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));

        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_Id(attemptId);
        List<AnswerInspectionDto> details = new ArrayList<>();

        for (QuizAnswer answer : answers) {
            List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId());
            QuizOption correctOpt = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElse(null);

            details.add(AnswerInspectionDto.builder()
                    .questionId(answer.getQuizQuestion().getId())
                    .questionContent(answer.getQuizQuestion().getContent())
                    .selectedOptionId(answer.getSelectedOption() != null ? answer.getSelectedOption().getId() : null)
                    .correctOptionId(correctOpt != null ? correctOpt.getId() : null)
                    .isCorrect(Boolean.TRUE.equals(answer.getIsCorrect()))
                    .options(options.stream().map(o -> new OptionDto(o.getId(), o.getContent(), Boolean.TRUE.equals(o.getIsCorrect()))).toList())
                    .build());
        }

        AttemptInspectionRes res = AttemptInspectionRes.builder()
                .attemptId(attempt.getId())
                .studentName(attempt.getUser().getFullName() != null ? attempt.getUser().getFullName() : attempt.getUser().getEmail())
                .studentEmail(attempt.getUser().getEmail())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .totalQuestions(attempt.getTotalQuestions())
                .submittedAt(attempt.getSubmittedAt())
                .answers(details)
                .build();

        return ResponseEntity.ok(res);
    }

    @Data
    @Builder
    public static class GradebookRes {
        private Long courseId;
        private String courseTitle;
        private int totalStudents;
        private int totalAttempts;
        private BigDecimal averageScore;
        private double passRatePercentage;
        private boolean hasOfficialQuiz;
        private List<StudentGradeDto> students;
    }

    @Data
    @Builder
    public static class StudentGradeDto {
        private Long userId;
        private String fullName;
        private String email;
        private int attemptCount;
        private BigDecimal highestScore;
        private BigDecimal latestScore;
        private LocalDateTime latestSubmittedAt;
        private boolean passed;
        private Long latestAttemptId;
    }

    @Data
    @Builder
    public static class AttemptInspectionRes {
        private Long attemptId;
        private String studentName;
        private String studentEmail;
        private BigDecimal score;
        private int correctCount;
        private int totalQuestions;
        private LocalDateTime submittedAt;
        private List<AnswerInspectionDto> answers;
    }

    @Data
    @Builder
    public static class AnswerInspectionDto {
        private Long questionId;
        private String questionContent;
        private Long selectedOptionId;
        private Long correctOptionId;
        private boolean isCorrect;
        private List<OptionDto> options;
    }

    @Data
    @Builder
    public static class OptionDto {
        private Long id;
        private String content;
        private boolean isCorrect;
    }
}
