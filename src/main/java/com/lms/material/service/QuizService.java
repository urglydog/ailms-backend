package com.lms.material.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.dto.QuizAttemptDto;
import com.lms.material.entity.*;
import com.lms.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public void setOfficial(String instructorEmail, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Chi giang vien cua khoa hoc moi co the danh dau Quiz chinh thuc");
        }
        
        quiz.setIsOfficial(true);
        quizRepository.save(quiz);
    }

    @Transactional
    public QuizAttemptDto.StartRes startAttempt(String studentEmail, Long courseId) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        
        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new AccessDeniedDomainException("Ban can dang ky khoa hoc nay de lam bai Quiz");
        }
        
        Quiz quiz = quizRepository.findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay bai Quiz chinh thuc nao cho khoa hoc", courseId));
        
        List<QuizQuestion> allQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId());
        Collections.shuffle(allQuestions);
        List<QuizQuestion> selectedQuestions = allQuestions.stream().limit(30).collect(Collectors.toList());
        
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUser(student);
        attempt.setScore(BigDecimal.ZERO);
        attempt.setTotalQuestions(selectedQuestions.size());
        attempt.setCorrectCount(0);
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt = quizAttemptRepository.save(attempt);
        
        List<QuizAttemptDto.QuestionDto> questionDtos = new ArrayList<>();
        
        for (QuizQuestion q : selectedQuestions) {
            QuizAnswer answer = new QuizAnswer();
            answer.setQuizAttempt(attempt);
            answer.setQuizQuestion(q);
            quizAnswerRepository.save(answer);
            
            List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(q.getId());
            List<QuizAttemptDto.OptionDto> optionDtos = options.stream()
                    .map(o -> new QuizAttemptDto.OptionDto(o.getId(), o.getContent()))
                    .collect(Collectors.toList());
                    
            questionDtos.add(new QuizAttemptDto.QuestionDto(q.getId(), q.getContent(), q.getDisplayOrder(), optionDtos));
        }
        
        return new QuizAttemptDto.StartRes(attempt.getId(), quiz.getId(), questionDtos);
    }

    @Transactional
    public QuizAttemptDto.SubmitRes submitAttempt(String studentEmail, Long attemptId, QuizAttemptDto.SubmitReq req) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen nop bai thi nay");
        }
        
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_Id(attemptId);
        int correctCount = 0;
        
        for (QuizAnswer answer : answers) {
            Long selectedOptionId = req.answers().get(answer.getQuizQuestion().getId());
            if (selectedOptionId != null) {
                QuizOption selectedOption = quizOptionRepository.findById(selectedOptionId).orElse(null);
                answer.setSelectedOption(selectedOption);
                if (selectedOption != null && Boolean.TRUE.equals(selectedOption.getIsCorrect())) {
                    answer.setIsCorrect(true);
                    correctCount++;
                } else {
                    answer.setIsCorrect(false);
                }
            } else {
                answer.setIsCorrect(false);
            }
            quizAnswerRepository.save(answer);
        }
        
        attempt.setCorrectCount(correctCount);
        BigDecimal score = BigDecimal.valueOf((double) correctCount / attempt.getTotalQuestions() * 10.0);
        attempt.setScore(score);
        attempt.setSubmittedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);
        
        return new QuizAttemptDto.SubmitRes(attemptId, score, correctCount, attempt.getTotalQuestions());
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptDto.HistoryRes> getAttemptHistory(String studentEmail, Long courseId) {
        Quiz quiz = quizRepository.findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay bai Quiz chinh thuc nao cho khoa hoc", courseId));
        
        return quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(studentEmail, quiz.getId()).stream()
                .map(a -> new QuizAttemptDto.HistoryRes(a.getId(), a.getScore(), a.getCorrectCount(), a.getTotalQuestions(), a.getSubmittedAt(), a.getQuiz().getId()))
                .collect(Collectors.toList());
    }
}
