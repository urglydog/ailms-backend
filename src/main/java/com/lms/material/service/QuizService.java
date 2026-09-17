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
    public void setOfficial(String instructorEmail, Long quizId, Boolean isOfficial) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Chi giang vien cua khoa hoc moi co the danh dau Quiz chinh thuc");
        }
        
        quiz.setIsOfficial(isOfficial != null ? isOfficial : true);
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
        question.setIsMultipleChoice(req.isMultipleChoice() != null ? req.isMultipleChoice() : false);
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
    public void addQuestion(String instructorEmail, Long quizId, com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }

        // Determine the next displayOrder
        long maxOrder = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quizId)
                .stream()
                .mapToLong(QuizQuestion::getDisplayOrder)
                .max()
                .orElse(0);

        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setContent(req.content());
        question.setIsMultipleChoice(req.isMultipleChoice() != null ? req.isMultipleChoice() : false);
        question.setDisplayOrder((int) maxOrder + 1);
        quizQuestionRepository.save(question);

        if (req.options() != null) {
            for (var optReq : req.options()) {
                QuizOption opt = new QuizOption();
                opt.setQuizQuestion(question);
                opt.setContent(optReq.content());
                opt.setIsCorrect(optReq.isCorrect());
                quizOptionRepository.save(opt);
            }
        }

        if (quiz.getQuestionCount() != null) {
            quiz.setQuestionCount(quiz.getQuestionCount() + 1);
        } else {
            quiz.setQuestionCount(1);
        }
        quizRepository.save(quiz);
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
    public void updatePersonalQuestion(String userEmail, Long questionId, com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", questionId));
        if (!question.getQuiz().getMaterialGeneration().getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        if (question.getQuiz().getIsOfficial() != null && question.getQuiz().getIsOfficial()) {
            throw new AccessDeniedDomainException("Khong the sua cau hoi cua hoc lieu Official");
        }
        
        question.setContent(req.content());
        question.setIsMultipleChoice(req.isMultipleChoice() != null ? req.isMultipleChoice() : false);
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
    public void addPersonalQuestion(String userEmail, Long materialId, com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        Quiz quiz = quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", materialId));
        if (!quiz.getMaterialGeneration().getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        if (quiz.getIsOfficial() != null && quiz.getIsOfficial()) {
            throw new AccessDeniedDomainException("Khong the them cau hoi vao hoc lieu Official");
        }

        long maxOrder = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId())
                .stream()
                .mapToLong(QuizQuestion::getDisplayOrder)
                .max()
                .orElse(0);

        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setContent(req.content());
        question.setIsMultipleChoice(req.isMultipleChoice() != null ? req.isMultipleChoice() : false);
        question.setDisplayOrder((int) maxOrder + 1);
        quizQuestionRepository.save(question);

        if (req.options() != null) {
            for (var optReq : req.options()) {
                QuizOption opt = new QuizOption();
                opt.setQuizQuestion(question);
                opt.setContent(optReq.content());
                opt.setIsCorrect(optReq.isCorrect());
                quizOptionRepository.save(opt);
            }
        }

        if (quiz.getQuestionCount() != null) {
            quiz.setQuestionCount(quiz.getQuestionCount() + 1);
        } else {
            quiz.setQuestionCount(1);
        }
        quizRepository.save(quiz);
    }

    @Transactional
    public void deletePersonalQuestion(String userEmail, Long questionId) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", questionId));
        if (!question.getQuiz().getMaterialGeneration().getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        if (question.getQuiz().getIsOfficial() != null && question.getQuiz().getIsOfficial()) {
            throw new AccessDeniedDomainException("Khong the xoa cau hoi cua hoc lieu Official");
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
                
        // Kiểm tra khung giờ mở/đóng thi sẽ được dời xuống sau khi check ongoing attempt
        LocalDateTime now = LocalDateTime.now();
        
        // Tìm xem có attempt nào đang làm dở (IN_PROGRESS) không
        QuizAttempt ongoingAttempt = quizAttemptRepository.findFirstByUser_EmailAndQuiz_IdAndStatusOrderByCreatedAtDesc(studentEmail, quiz.getId(), "IN_PROGRESS");
        
        if (ongoingAttempt != null) {
            // Kiểm tra xem attempt này đã hết giờ chưa (dựa vào createdAt + duration)
            if (quiz.getDurationMinutes() != null) {
                LocalDateTime expireTime = ongoingAttempt.getCreatedAt().plusMinutes(quiz.getDurationMinutes());
                if (now.isAfter(expireTime)) {
                    // Đã hết giờ, tự động đánh dấu hoàn thành với điểm 0
                    ongoingAttempt.setStatus("COMPLETED");
                    ongoingAttempt.setSubmittedAt(now);
                    quizAttemptRepository.save(ongoingAttempt);
                    ongoingAttempt = null; // Bỏ qua, tiếp tục tạo attempt mới nếu còn lượt
                }
            }
        }

        // Nếu vẫn còn ongoingAttempt hợp lệ, trả về ngay để sinh viên làm tiếp
        if (ongoingAttempt != null) {
            List<QuizAttemptDto.QuestionDto> questionDtos = new ArrayList<>();
            List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_Id(ongoingAttempt.getId());
            for (QuizAnswer ans : answers) {
                QuizQuestion q = ans.getQuizQuestion();
                List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(q.getId());
                List<QuizAttemptDto.OptionDto> optionDtos = options.stream()
                        .map(o -> new QuizAttemptDto.OptionDto(o.getId(), o.getContent()))
                        .collect(Collectors.toList());
                questionDtos.add(new QuizAttemptDto.QuestionDto(q.getId(), q.getContent(), q.getDisplayOrder(), Boolean.TRUE.equals(q.getIsMultipleChoice()), optionDtos));
            }
            return new QuizAttemptDto.StartRes(
                    ongoingAttempt.getId(), 
                    quiz.getId(), 
                    questionDtos,
                    quiz.getIsProctored(),
                    quiz.getMaxViolations(),
                    quiz.getDurationMinutes(),
                    ongoingAttempt.getCreatedAt()
            );
        }

        // BÂY GIỜ mới kiểm tra khung giờ mở/đóng thi cho attempt mới
        if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
            throw new AccessDeniedDomainException("Bai thi chua mo. Thoi gian mo: " + quiz.getStartTime());
        }
        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            throw new AccessDeniedDomainException("Bai thi da ket thuc vao luc: " + quiz.getEndTime());
        }

        // Kiểm tra số lần thi
        if (quiz.getMaxAttempts() != null) {
            long attemptCount = quizAttemptRepository.findByUser_EmailAndQuiz_Id(studentEmail, quiz.getId()).size();
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
        attempt.setStatus("IN_PROGRESS");
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
                    
            questionDtos.add(new QuizAttemptDto.QuestionDto(q.getId(), q.getContent(), q.getDisplayOrder(), Boolean.TRUE.equals(q.getIsMultipleChoice()), optionDtos));
        }
        
        return new QuizAttemptDto.StartRes(
                attempt.getId(), 
                quiz.getId(), 
                questionDtos,
                quiz.getIsProctored(),
                quiz.getMaxViolations(),
                quiz.getDurationMinutes(),
                attempt.getCreatedAt()
        );
    }


    @Transactional
    public QuizAttemptDto.SubmitRes submitAttempt(String studentEmail, Long attemptId, QuizAttemptDto.SubmitReq req) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen nop bai thi nay");
        }
        
        if ("COMPLETED".equals(attempt.getStatus())) {
            throw new AccessDeniedDomainException("Bai thi nay da duoc nop");
        }
        
        boolean isArchived = false;
        if (Boolean.TRUE.equals(attempt.getQuiz().getIsDeleted())) {
            if (attempt.getQuiz().getDeletedAt() != null && attempt.getCreatedAt() != null) {
                long createdMillis = attempt.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long deletedMillis = attempt.getQuiz().getDeletedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (createdMillis < deletedMillis) {
                    isArchived = true;
                } else {
                    throw new com.lms.common.exception.ResourceGoneException("Bài tập này đã được giảng viên thu hồi.");
                }
            } else {
                throw new com.lms.common.exception.ResourceGoneException("Bài tập này đã được giảng viên thu hồi.");
            }
        }
        
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_Id(attemptId);
        int correctCount = 0;
        
        List<QuizAttemptDto.AnswerDetailDto> details = new ArrayList<>();
        boolean allowReview = Boolean.TRUE.equals(attempt.getQuiz().getAllowReview());
        
        for (QuizAnswer answer : answers) {
            List<Long> selectedOptionIds = req.answers().get(answer.getQuizQuestion().getId());
            
            List<QuizOption> correctOpts = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).toList();
            List<Long> correctOptionIds = correctOpts.stream().map(QuizOption::getId).toList();
            
            boolean isCorrect = false;
            if (selectedOptionIds != null && !selectedOptionIds.isEmpty()) {
                String idsStr = selectedOptionIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                answer.setSelectedOptionIds(idsStr);
                
                // Logic: ALL OR NOTHING for all question types.
                if (selectedOptionIds.size() == correctOptionIds.size() && selectedOptionIds.containsAll(correctOptionIds)) {
                    isCorrect = true;
                }
            } else {
                answer.setSelectedOptionIds("");
            }
            
            answer.setIsCorrect(isCorrect);
            if (isCorrect) {
                correctCount++;
            }
            quizAnswerRepository.save(answer);
            
            List<QuizAttemptDto.OptionDto> options = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().map(o -> new QuizAttemptDto.OptionDto(o.getId(), o.getContent())).toList();
                    
            details.add(new QuizAttemptDto.AnswerDetailDto(
                    answer.getQuizQuestion().getId(),
                    answer.getQuizQuestion().getContent(),
                    selectedOptionIds != null ? selectedOptionIds : new ArrayList<>(),
                    allowReview ? correctOptionIds : null, // Ẩn đáp án đúng nếu allowReview = false
                    allowReview ? answer.getIsCorrect() : null, // Ẩn kết quả Đúng/Sai nếu allowReview = false
                    options
            ));
        }
        
        attempt.setCorrectCount(correctCount);
        BigDecimal score = BigDecimal.valueOf((double) correctCount / attempt.getTotalQuestions() * 10.0);
        attempt.setScore(score);
        attempt.setStatus("COMPLETED");
        attempt.setSubmittedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);
        
        return new QuizAttemptDto.SubmitRes(attemptId, score, correctCount, attempt.getTotalQuestions(), details, isArchived);
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.SubmitRes getAttemptDetail(String studentEmail, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen xem bai thi nay");
        }
        
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttempt_Id(attemptId);
        List<QuizAttemptDto.AnswerDetailDto> details = new ArrayList<>();
        boolean allowReview = Boolean.TRUE.equals(attempt.getQuiz().getAllowReview());
        
        for (QuizAnswer answer : answers) {
            List<QuizOption> correctOpts = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).toList();
            List<Long> correctOptionIds = correctOpts.stream().map(QuizOption::getId).toList();
            
            List<Long> selectedIds = new ArrayList<>();
            if (answer.getSelectedOptionIds() != null && !answer.getSelectedOptionIds().isEmpty()) {
                selectedIds = java.util.Arrays.stream(answer.getSelectedOptionIds().split(","))
                        .map(Long::parseLong).toList();
            } else if (answer.getSelectedOption() != null) {
                selectedIds.add(answer.getSelectedOption().getId());
            }
                    
            List<QuizAttemptDto.OptionDto> options = quizOptionRepository.findByQuizQuestion_Id(answer.getQuizQuestion().getId())
                    .stream().map(o -> new QuizAttemptDto.OptionDto(o.getId(), o.getContent())).toList();
                    
            details.add(new QuizAttemptDto.AnswerDetailDto(
                    answer.getQuizQuestion().getId(),
                    answer.getQuizQuestion().getContent(),
                    selectedIds,
                    allowReview ? correctOptionIds : null,
                    allowReview ? answer.getIsCorrect() : null,
                    options
            ));
        }
        
        return new QuizAttemptDto.SubmitRes(attemptId, attempt.getScore(), attempt.getCorrectCount(), attempt.getTotalQuestions(), details, Boolean.TRUE.equals(attempt.getQuiz().getIsDeleted()));
    }


    @Transactional(readOnly = true)
    public List<QuizAttemptDto.HistoryRes> getAttemptHistory(String studentEmail, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        
        return quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(studentEmail, quiz.getId()).stream()
                .map(a -> new QuizAttemptDto.HistoryRes(a.getId(), a.getScore(), a.getCorrectCount(), a.getTotalQuestions(), a.getSubmittedAt(), a.getQuiz().getId(), a.getStatus(), Boolean.TRUE.equals(a.getQuiz().getIsDeleted()), Boolean.TRUE.equals(a.getQuiz().getAllowReview())))
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
                
        if (Boolean.FALSE.equals(question.getQuiz().getAllowReview())) {
            throw new AccessDeniedDomainException("Bai thi nay khong cho phep xem lai dap an");
        }
                
        // Ensure student actually took this quiz (basic authorization)
        // For simplicity, we just pass the question directly to AI
        
        List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(question.getId());
        QuizOption correctOption = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElse(null);
        QuizOption selectedOption = options.stream().filter(o -> o.getId().equals(req.selectedOptionId())).findFirst().orElse(null);
        
        if (correctOption == null) throw new IllegalArgumentException("Khong tim thay dap an dung");
        
        // Lấy ngôn ngữ từ MaterialGeneration để AI trả lời đúng ngôn ngữ của bộ Quiz (Task 2)
        String quizLanguage = question.getQuiz().getMaterialGeneration().getLanguage();
        String languageInstruction = (quizLanguage != null && !quizLanguage.isBlank())
                ? "CRITICAL RULE: YOU MUST WRITE YOUR ENTIRE RESPONSE IN THE LANGUAGE CORRESPONDING TO CODE '" + quizLanguage + "'. (e.g. if 'ja', you MUST reply entirely in Japanese). DO NOT USE VIETNAMESE OR ENGLISH. THIS IS STRICTLY REQUIRED.\n\n"
                : "";

        String prompt = languageInstruction + "Explain why the answer I chose is wrong and why the correct answer is right.\n" +
                "Question: " + question.getContent() + "\n" +
                "Options:\n" +
                options.stream().map(o -> "- " + o.getContent()).collect(Collectors.joining("\n")) + "\n" +
                "Correct answer: " + correctOption.getContent() + "\n" +
                "My answer: " + (selectedOption != null ? selectedOption.getContent() : "None selected") + "\n" +
                "Please provide a concise, clear, and educational explanation.";
                
        Map<String, Object> payload = Map.of(
                "question", prompt,
                "lesson_id", -1, // Not bound to a specific lesson, just a general explanation
                "history", List.of(),
                "attachments", List.of(),
                "language", quizLanguage != null ? quizLanguage : ""
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
