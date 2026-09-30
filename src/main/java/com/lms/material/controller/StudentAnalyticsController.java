package com.lms.material.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.entity.QuizAnswer;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.entity.QuizQuestion;
import com.lms.material.repository.QuizAnswerRepository;
import com.lms.material.repository.QuizAttemptRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class StudentAnalyticsController {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;

    @GetMapping("/knowledge-gaps")
    @Transactional(readOnly = true)
    public ResponseEntity<KnowledgeGapsRes> getKnowledgeGaps(Principal principal, @PathVariable Long courseId) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getName()));

        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), courseId)) {
            throw new com.lms.common.exception.AccessDeniedDomainException("Ban chua ghi danh khoa hoc nay");
        }

        List<QuizAttempt> recentAttempts = quizAttemptRepository.findRecentTop5AttemptsPerQuiz(user.getId(), courseId);
        if (recentAttempts.isEmpty()) {
            return ResponseEntity.ok(new KnowledgeGapsRes(Collections.emptyList()));
        }

        List<Long> attemptIds = recentAttempts.stream().map(QuizAttempt::getId).toList();
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_IdIn(attemptIds);

        // Map attempts by ID to quickly find submission time
        Map<Long, QuizAttempt> attemptMap = recentAttempts.stream()
                .collect(Collectors.toMap(QuizAttempt::getId, a -> a));

        // Groups: topicTag -> List<QuizAnswer>
        Map<String, List<QuizAnswer>> answersByTopic = answers.stream()
                .filter(a -> a.getQuizQuestion().getTopicTag() != null && !a.getQuizQuestion().getTopicTag().isBlank())
                .collect(Collectors.groupingBy(a -> a.getQuizQuestion().getTopicTag()));

        List<TopicGapDto> gaps = new ArrayList<>();

        for (Map.Entry<String, List<QuizAnswer>> entry : answersByTopic.entrySet()) {
            String topic = entry.getKey();
            List<QuizAnswer> topicAnswers = entry.getValue();

            // Find latest attempt that contained this topic
            QuizAnswer latestAnswer = topicAnswers.stream()
                    .max(Comparator.comparing(a -> attemptMap.get(a.getQuizAttempt().getId()).getSubmittedAt()))
                    .orElse(null);

            if (latestAnswer == null) continue;

            // Check if latest attempt for this topic is 100% correct
            QuizAttempt latestAttempt = attemptMap.get(latestAnswer.getQuizAttempt().getId());
            boolean allCorrectInLatest = topicAnswers.stream()
                    .filter(a -> a.getQuizAttempt().getId().equals(latestAttempt.getId()))
                    .allMatch(a -> Boolean.TRUE.equals(a.getIsCorrect()));

            if (allCorrectInLatest) {
                continue; // Resolved
            }

            long totalQuestions = topicAnswers.size();
            long incorrectQuestions = topicAnswers.stream().filter(a -> !Boolean.TRUE.equals(a.getIsCorrect())).count();

            if (incorrectQuestions == 0) continue;

            double errorRate = (double) incorrectQuestions / totalQuestions;

            // Find a representative incorrect question for review
            QuizQuestion repQuestion = topicAnswers.stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()))
                    .max(Comparator.comparing(a -> attemptMap.get(a.getQuizAttempt().getId()).getSubmittedAt()))
                    .map(QuizAnswer::getQuizQuestion)
                    .orElse(null);

            gaps.add(new TopicGapDto(
                    topic,
                    errorRate,
                    repQuestion != null ? repQuestion.getVideoTimestamp() : null,
                    repQuestion != null ? repQuestion.getReferenceLessonId() : null,
                    incorrectQuestions,
                    totalQuestions
            ));
        }

        // Sort by errorRate desc
        gaps.sort((a, b) -> Double.compare(b.getErrorRate(), a.getErrorRate()));

        // Return top 5 gaps
        if (gaps.size() > 5) {
            gaps = gaps.subList(0, 5);
        }

        return ResponseEntity.ok(new KnowledgeGapsRes(gaps));
    }

    @Data
    public static class KnowledgeGapsRes {
        private final List<TopicGapDto> gaps;
    }

    @Data
    public static class TopicGapDto {
        private final String topic;
        private final double errorRate;
        private final Integer videoTimestamp;
        private final Long referenceLessonId;
        private final long incorrectCount;
        private final long totalCount;
    }
}
