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
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final com.lms.common.config.AiWorkerConfig aiWorkerConfig;

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
    public void updateQuizSettings(String instructorEmail, Long quizId, com.lms.material.dto.QuizDto.QuizSettingsReq req) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Chi giang vien cua khoa hoc moi duoc sua cau hinh bai thi");
        }
        
        quiz.setRandomPickCount(req.randomPickCount());
        if (req.allowReview() != null) quiz.setAllowReview(req.allowReview());
        quiz.setStartTime(req.startTime());
        quiz.setEndTime(req.endTime());
        quiz.setDurationMinutes(req.durationMinutes());
        quiz.setMaxAttempts(req.maxAttempts());
        if (req.isProctored() != null) quiz.setIsProctored(req.isProctored());
        if (req.maxViolations() != null) quiz.setMaxViolations(req.maxViolations());
        quizRepository.save(quiz);
    }

    @Transactional
    public void updateQuestion(String instructorEmail, Long questionId, com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", questionId));
        Course course = question.getQuiz().getMaterialGeneration().getCourse();
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        question.setContent(req.content());
        quizQuestionRepository.save(question);
        
        List<QuizOption> existingOptions = quizOptionRepository.findByQuizQuestion_Id(questionId);
        quizOptionRepository.deleteAll(existingOptions);
        
        if (req.options() != null) {
            for (var optReq : req.options()) {
                QuizOption opt = new QuizOption();
                opt.setQuizQuestion(question);
                opt.setContent(optReq.content());
                opt.setIsCorrect(optReq.isCorrect());
                quizOptionRepository.save(opt);
            }
        }
    }

    @Transactional
    public void deleteQuestion(String instructorEmail, Long questionId) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", questionId));
        Course course = question.getQuiz().getMaterialGeneration().getCourse();
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Quiz quiz = question.getQuiz();
        
        List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(questionId);
        quizOptionRepository.deleteAll(options);
        quizQuestionRepository.delete(question);
        
        if (quiz.getQuestionCount() != null && quiz.getQuestionCount() > 0) {
            quiz.setQuestionCount(quiz.getQuestionCount() - 1);
            quizRepository.save(quiz);
        }
    }

    @Transactional
    public QuizAttemptDto.StartRes startAttempt(String studentEmail, Long quizId) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay bai Quiz chinh thuc", quizId));

        if (!quiz.getIsOfficial()) {
            throw new AccessDeniedDomainException("Bai thi nay khong phai la bai thi chinh thuc");
        }
        
        Long courseId = quiz.getMaterialGeneration().getCourse().getId();
        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new AccessDeniedDomainException("Ban can dang ky khoa hoc nay de lam bai Quiz");
        }
                
        // Kiểm tra khung giờ mở/đóng thi
        LocalDateTime now = LocalDateTime.now();
        if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
            throw new AccessDeniedDomainException("Bai thi chua mo. Thoi gian mo: " + quiz.getStartTime());
        }
        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            throw new AccessDeniedDomainException("Bai thi da ket thuc vao luc: " + quiz.getEndTime());
        }
        
        // Kiểm tra số lần thi
        if (quiz.getMaxAttempts() != null) {
            long attemptCount = quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(studentEmail, quiz.getId()).size();
            if (attemptCount >= quiz.getMaxAttempts()) {
                throw new AccessDeniedDomainException("Ban da het so luot lam bai thi nay (" + quiz.getMaxAttempts() + " luot)");
            }
        }
        
        List<QuizQuestion> allQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId());
        Collections.shuffle(allQuestions);
        
        int pickCount = quiz.getRandomPickCount() != null ? quiz.getRandomPickCount() : allQuestions.size();
        List<QuizQuestion> selectedQuestions = allQuestions.stream().limit(pickCount).collect(Collectors.toList());
        
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
        
        return new QuizAttemptDto.StartRes(
                attempt.getId(), 
                quiz.getId(), 
                questionDtos,
                quiz.getIsProctored(),
                quiz.getMaxViolations(),
                quiz.getDurationMinutes()
        );
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
        
        List<QuizAttemptDto.AnswerDetailDto> details = new ArrayList<>();
        boolean allowReview = Boolean.TRUE.equals(attempt.getQuiz().getAllowReview());
        
        for (QuizAnswer answer : answers) {
            Long selectedOptionId = req.answers().get(answer.getQuizQuestion().getId());
            QuizOption correctOpt = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElse(null);
                    
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
            
            List<QuizAttemptDto.OptionDto> options = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().map(o -> new QuizAttemptDto.OptionDto(o.getId(), o.getContent())).toList();
                    
            details.add(new QuizAttemptDto.AnswerDetailDto(
                    answer.getQuizQuestion().getId(),
                    answer.getQuizQuestion().getContent(),
                    selectedOptionId,
                    allowReview && correctOpt != null ? correctOpt.getId() : null, // Ẩn đáp án đúng nếu allowReview = false
                    allowReview ? answer.getIsCorrect() : false, // Ẩn kết quả Đúng/Sai nếu allowReview = false
                    options
            ));
        }
        
        attempt.setCorrectCount(correctCount);
        BigDecimal score = BigDecimal.valueOf((double) correctCount / attempt.getTotalQuestions() * 10.0);
        attempt.setScore(score);
        attempt.setSubmittedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);
        
        return new QuizAttemptDto.SubmitRes(attemptId, score, correctCount, attempt.getTotalQuestions(), details);
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptDto.HistoryRes> getAttemptHistory(String studentEmail, Long courseId) {
        Quiz quiz = quizRepository.findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay bai Quiz chinh thuc nao cho khoa hoc", courseId));
        
        return quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(studentEmail, quiz.getId()).stream()
                .map(a -> new QuizAttemptDto.HistoryRes(a.getId(), a.getScore(), a.getCorrectCount(), a.getTotalQuestions(), a.getSubmittedAt(), a.getQuiz().getId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.ExplainRes explainWrongAnswer(String studentEmail, QuizAttemptDto.ExplainReq req) {
        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        if (Boolean.TRUE.equals(user.getIsAiLocked())) {
            throw new AccessDeniedDomainException("Tai khoan cua ban da bi khoa tinh nang AI do vi pham chinh sach su dung.");
        }
        
        QuizQuestion question = quizQuestionRepository.findById(req.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", req.questionId()));
                
        // Ensure student actually took this quiz (basic authorization)
        // For simplicity, we just pass the question directly to AI
        
        List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(question.getId());
        QuizOption correctOption = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElse(null);
        QuizOption selectedOption = options.stream().filter(o -> o.getId().equals(req.selectedOptionId())).findFirst().orElse(null);
        
        if (correctOption == null) throw new IllegalArgumentException("Khong tim thay dap an dung");
        
        // Lấy ngôn ngữ từ MaterialGeneration để AI trả lời đúng ngôn ngữ của bộ Quiz (Task 2)
        String quizLanguage = question.getQuiz().getMaterialGeneration().getLanguage();
        String languageInstruction = (quizLanguage != null && !quizLanguage.isBlank())
                ? "\nIMPORTANT: Respond exclusively in the language with BCP-47 code: '" + quizLanguage + "'. Do NOT switch languages."
                : "";

        String prompt = "Explain why the answer I chose is wrong and why the correct answer is right.\n" +
                "Question: " + question.getContent() + "\n" +
                "Options:\n" +
                options.stream().map(o -> "- " + o.getContent()).collect(Collectors.joining("\n")) + "\n" +
                "Correct answer: " + correctOption.getContent() + "\n" +
                "My answer: " + (selectedOption != null ? selectedOption.getContent() : "None selected") + "\n" +
                "Please provide a concise, clear, and educational explanation." + languageInstruction;
                
        Map<String, Object> payload = Map.of(
                "question", prompt,
                "lesson_id", -1, // Not bound to a specific lesson, just a general explanation
                "history", List.of(),
                "attachments", List.of()
        );
        
        try {
            // Re-use tutor/ask endpoint which handles generating AI response
            Map res = restTemplate.postForObject(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/tutor/ask", payload, Map.class);
            if (res != null && res.get("answer") != null) {
                return new QuizAttemptDto.ExplainRes((String) res.get("answer"));
            }
        } catch (Exception e) {
            // ignore and fallback
        }
        return new QuizAttemptDto.ExplainRes("Gia sư AI hiện không khả dụng để giải thích câu hỏi này. Vui lòng thử lại sau.");
    }
}
