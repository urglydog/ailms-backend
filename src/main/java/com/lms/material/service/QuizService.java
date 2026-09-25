package com.lms.material.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.common.storage.StorageService;
import com.lms.material.dto.QuizAttemptDto;
import com.lms.material.entity.*;
import com.lms.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private final com.lms.enrollment.service.LessonProgressService lessonProgressService;
    private final QuizAttemptViolationRepository quizAttemptViolationRepository;
    private final ProctoringRecordingRepository proctoringRecordingRepository;
    private final StorageService storageService;
    private final Tika tika = new Tika();

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

    /**
     * Task 4 — Import CSV hàng loạt câu hỏi Quiz (phía giảng viên). Cột: Question Type
     * (SINGLE/MULTI), Question Text, Option A-D, Correct Answer (vd "A" hoặc "A,C" nếu MULTI).
     * Cột "Explanation" trong file mẫu bị bỏ qua khi đọc — QuizQuestion không có field này
     * (đã cố ý xoá theo BR-QUIZ-01/02, xem docblock QuizQuestion). Lỗi từng dòng được gom lại
     * thay vì fail cả import, để giảng viên sửa và tải lại đúng những dòng lỗi.
     */
    @Transactional
    public com.lms.material.dto.QuizDto.ImportResultRes addQuestionsFromCsv(
            String instructorEmail, Long quizId, org.springframework.web.multipart.MultipartFile file) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }

        long nextOrder = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quizId)
                .stream()
                .mapToLong(QuizQuestion::getDisplayOrder)
                .max()
                .orElse(0) + 1;

        List<String> errors = new ArrayList<>();
        int imported = 0;

        try (var reader = new com.opencsv.CSVReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            for (int i = 1; i < rows.size(); i++) { // dòng 0 là header
                String[] row = rows.get(i);
                int lineNo = i + 1;
                try {
                    if (row.length < 7 || row[1] == null || row[1].isBlank()) continue; // dòng trống, bỏ qua êm

                    boolean isMultiple = row[0].trim().equalsIgnoreCase("MULTI");
                    String questionText = row[1].trim();
                    String[] optionTexts = { row[2].trim(), row[3].trim(), row[4].trim(), row[5].trim() };
                    Set<String> correctLetters = Arrays.stream(row[6].split(","))
                            .map(String::trim).map(String::toUpperCase)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());

                    if (questionText.isEmpty()) {
                        errors.add("Dòng " + lineNo + ": thiếu nội dung câu hỏi");
                        continue;
                    }
                    if (correctLetters.isEmpty()) {
                        errors.add("Dòng " + lineNo + ": thiếu đáp án đúng");
                        continue;
                    }

                    QuizQuestion question = new QuizQuestion();
                    question.setQuiz(quiz);
                    question.setContent(questionText);
                    question.setIsMultipleChoice(isMultiple);
                    question.setDisplayOrder((int) nextOrder++);
                    quizQuestionRepository.save(question);

                    String[] letters = { "A", "B", "C", "D" };
                    for (int optIdx = 0; optIdx < 4; optIdx++) {
                        if (optionTexts[optIdx].isEmpty()) continue;
                        QuizOption opt = new QuizOption();
                        opt.setQuizQuestion(question);
                        opt.setContent(optionTexts[optIdx]);
                        opt.setIsCorrect(correctLetters.contains(letters[optIdx]));
                        quizOptionRepository.save(opt);
                    }

                    imported++;
                } catch (Exception rowEx) {
                    errors.add("Dòng " + lineNo + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file CSV: " + e.getMessage());
        }

        if (imported > 0) {
            quiz.setQuestionCount((quiz.getQuestionCount() != null ? quiz.getQuestionCount() : 0) + imported);
            quizRepository.save(quiz);
        }

        return new com.lms.material.dto.QuizDto.ImportResultRes(imported, errors);
    }

    /**
     * UpComming_Plan.md A5 — Export PDF đề trắng (mode=blank, đáp án in riêng trang cuối) hoặc
     * cheatsheet (mode=cheatsheet, câu hỏi + đáp án đúng in liền nhau để ôn nhanh). Dùng chung
     * OpenPDF vừa thêm ở A1 (Certificate), không thêm lib PDF thứ 2.
     */
    @Transactional(readOnly = true)
    public byte[] exportQuizPdf(Quiz quiz, boolean cheatsheet) {
        List<QuizQuestion> questions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId());
        try {
            var document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 45, 45, 50, 50);
            var out = new java.io.ByteArrayOutputStream();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();

            // Base-14 Helvetica không có glyph tiếng Việt — dùng DejaVu Sans nhúng (VietnamesePdfFonts).
            com.lowagie.text.pdf.BaseFont bfRegular = com.lms.common.util.VietnamesePdfFonts.loadRegular();
            com.lowagie.text.pdf.BaseFont bfBold = com.lms.common.util.VietnamesePdfFonts.loadBold();
            var titleFont = new com.lowagie.text.Font(bfBold, 18);
            var questionFont = new com.lowagie.text.Font(bfBold, 12);
            var optionFont = new com.lowagie.text.Font(bfRegular, 11);
            var correctFont = new com.lowagie.text.Font(bfBold, 11, com.lowagie.text.Font.NORMAL, new java.awt.Color(5, 150, 105));

            String title = quiz.getMaterialGeneration() != null && quiz.getMaterialGeneration().getTitle() != null
                    ? quiz.getMaterialGeneration().getTitle()
                    : "Đề thi";
            var titlePar = new com.lowagie.text.Paragraph(cheatsheet ? title + " — Cheatsheet" : title, titleFont);
            titlePar.setSpacingAfter(20);
            document.add(titlePar);

            java.util.List<String> answerKey = new java.util.ArrayList<>();
            String[] letters = { "A", "B", "C", "D" };

            int idx = 1;
            for (QuizQuestion q : questions) {
                var qPar = new com.lowagie.text.Paragraph("Câu " + idx + ". " + q.getContent(), questionFont);
                qPar.setSpacingBefore(10);
                qPar.setSpacingAfter(6);
                document.add(qPar);

                List<QuizOption> options = quizOptionRepository.findByQuizQuestion_Id(q.getId());
                StringBuilder correctLetters = new StringBuilder();
                for (int i = 0; i < options.size(); i++) {
                    QuizOption opt = options.get(i);
                    String letter = i < letters.length ? letters[i] : String.valueOf(i + 1);
                    boolean isCorrect = Boolean.TRUE.equals(opt.getIsCorrect());
                    if (isCorrect) {
                        if (correctLetters.length() > 0) correctLetters.append(", ");
                        correctLetters.append(letter);
                    }
                    var optPar = new com.lowagie.text.Paragraph(
                            "   " + letter + ". " + opt.getContent(),
                            cheatsheet && isCorrect ? correctFont : optionFont);
                    document.add(optPar);
                }
                answerKey.add("Câu " + idx + ": " + correctLetters);
                idx++;
            }

            if (!cheatsheet) {
                document.newPage();
                var keyTitle = new com.lowagie.text.Paragraph("ĐÁP ÁN", titleFont);
                keyTitle.setSpacingAfter(15);
                document.add(keyTitle);
                for (String line : answerKey) {
                    document.add(new com.lowagie.text.Paragraph(line, optionFont));
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không sinh được PDF đề thi", e);
        }
    }

    /** A5 — export PDF phía giảng viên (đề của khoá học họ dạy). */
    @Transactional(readOnly = true)
    public byte[] exportQuizPdfAsInstructor(String instructorEmail, Long quizId, boolean cheatsheet) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        Course course = quiz.getMaterialGeneration().getCourse();
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        return exportQuizPdf(quiz, cheatsheet);
    }

    /** A5 — export PDF phía học viên (bộ quiz cá nhân của chính họ). */
    @Transactional(readOnly = true)
    public byte[] exportQuizPdfAsOwner(String userEmail, Long quizId, boolean cheatsheet) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
        if (!quiz.getMaterialGeneration().getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        return exportQuizPdf(quiz, cheatsheet);
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

        // A2 (UpComming_Plan.md) — Quiz chính thức giờ chiếm 30% công thức % tiến độ khóa học,
        // nên nộp bài xong phải tính lại ngay, không chỉ lúc xem video mới tính (LessonProgressService).
        if (Boolean.TRUE.equals(attempt.getQuiz().getIsOfficial())) {
            lessonProgressService.recalculateEnrollmentProgress(
                    attempt.getUser(), attempt.getQuiz().getMaterialGeneration().getCourse());
        }

        // UC-ANTICHEAT (25/09/2026) — Composite Risk Scoring: chỉ 1 lần/attempt, lúc nộp bài.
        // Gemini tổng hợp TOÀN BỘ tín hiệu hành vi đã ghi nhận trong quiz_attempt_violations
        // thành 1 nhận định rủi ro có giải thích, thay vì giảng viên chỉ thấy số đếm thô.
        // Lỗi ở đây KHÔNG được làm hỏng việc nộp bài (fail-open, giống mọi tích hợp AI phụ khác
        // trong dự án) — điểm số đã chốt xong ở trên trước khi bước này chạy.
        if (Boolean.TRUE.equals(attempt.getQuiz().getIsProctored())) {
            assessRisk(attempt);
        }

        return new QuizAttemptDto.SubmitRes(attemptId, score, correctCount, attempt.getTotalQuestions(), details, isArchived,
                attempt.getAiRiskLevel(), attempt.getAiRiskExplanation());
    }

    /** Xem docblock ở lời gọi trong {@link #submitAttempt}. */
    private void assessRisk(QuizAttempt attempt) {
        try {
            List<QuizAttemptViolation> violations = quizAttemptViolationRepository.findByAttempt_IdOrderByCreatedAtAsc(attempt.getId());
            Map<String, Long> violationCounts = violations.stream()
                    .collect(Collectors.groupingBy(QuizAttemptViolation::getType, Collectors.counting()));

            long durationSec = attempt.getCreatedAt() != null
                    ? java.time.Duration.between(attempt.getCreatedAt(), LocalDateTime.now()).getSeconds()
                    : 0;

            Map<String, Object> payload = Map.of(
                    "violation_counts", violationCounts,
                    "duration_sec", durationSec,
                    "question_count", attempt.getTotalQuestions()
            );

            Map res = restTemplate.postForObject(aiWorkerConfig.getBaseUrl() + "/api/v1/proctoring/assess-risk", payload, Map.class);
            if (res != null) {
                attempt.setAiRiskLevel((String) res.get("risk_level"));
                attempt.setAiRiskExplanation((String) res.get("explanation"));
                quizAttemptRepository.save(attempt);
            }
        } catch (Exception e) {
            // Fail-open — xem docblock lời gọi.
        }
    }

    /** UC-ANTICHEAT — ghi nhận 1 vi phạm rời rạc (rule-based, tức thời) trong lúc làm bài. Nguồn
     * thật thay cho {@code localStorage} trước đây (client-trust, mất sạch khi đóng tab). */
    @Transactional
    public QuizAttemptDto.ViolationRes recordViolation(String studentEmail, Long attemptId, QuizAttemptDto.ViolationReq req) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen ghi nhan vi pham cho bai thi nay");
        }
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            // Bài đã nộp — không còn gì để ghi nhận, tránh vi phạm "ma" tới muộn.
            return new QuizAttemptDto.ViolationRes(attempt.getViolationCount(), attempt.getQuiz().getMaxViolations(), false);
        }

        QuizAttemptViolation violation = new QuizAttemptViolation();
        violation.setAttempt(attempt);
        violation.setType(req.type());
        violation.setDetail(req.detail());
        quizAttemptViolationRepository.save(violation);

        int newCount = attempt.getViolationCount() + 1;
        attempt.setViolationCount(newCount);
        quizAttemptRepository.save(attempt);

        Integer maxViolations = attempt.getQuiz().getMaxViolations();
        boolean shouldAutoSubmit = maxViolations != null && newCount >= maxViolations;
        return new QuizAttemptDto.ViolationRes(newCount, maxViolations, shouldAutoSubmit);
    }

    /** UC-ANTICHEAT — xác minh khung hình webcam bằng Gemini Vision thật (đếm người + đánh giá
     * hướng nhìn), gọi đồng bộ AI-worker đúng khuôn {@link #explainWrongAnswer}. Bất thường thì
     * ghi nhận luôn qua {@link #recordViolation} — dùng chung 1 đường ghi nhận, không tách logic. */
    @Transactional
    public QuizAttemptDto.ProctorFrameRes analyzeProctorFrame(String studentEmail, Long attemptId, QuizAttemptDto.ProctorFrameReq req) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen gui khung hinh cho bai thi nay");
        }
        if (!Boolean.TRUE.equals(attempt.getQuiz().getIsProctored())) {
            throw new AccessDeniedDomainException("Bai thi nay khong bat giam sat AI");
        }

        Map<String, Object> payload = Map.of("image_base64", req.imageBase64(), "mime_type", req.mimeType());
        Map res;
        try {
            res = restTemplate.postForObject(aiWorkerConfig.getBaseUrl() + "/api/v1/proctoring/analyze-frame", payload, Map.class);
        } catch (Exception e) {
            // AI-worker khong phan hoi duoc — khong tinh la vi pham (fail-open), chi bo qua lan quet nay.
            return new QuizAttemptDto.ProctorFrameRes(null, null, false, attempt.getViolationCount(), attempt.getQuiz().getMaxViolations(), false);
        }

        Integer personCount = res != null && res.get("person_count") != null ? ((Number) res.get("person_count")).intValue() : null;
        String gazeDirection = res != null ? (String) res.get("gaze_direction") : null;

        boolean flagged = (personCount != null && personCount != 1) || (gazeDirection != null && !"screen".equals(gazeDirection));
        if (!flagged) {
            return new QuizAttemptDto.ProctorFrameRes(personCount, gazeDirection, false, attempt.getViolationCount(), attempt.getQuiz().getMaxViolations(), false);
        }

        String type = personCount != null && personCount == 0 ? "NO_FACE"
                : personCount != null && personCount > 1 ? "MULTIPLE_FACES"
                : "GAZE_AWAY";
        String detail = "person_count=" + personCount + ", gaze_direction=" + gazeDirection;
        QuizAttemptDto.ViolationRes violationRes = recordViolation(studentEmail, attemptId,
                new QuizAttemptDto.ViolationReq(type, detail));

        return new QuizAttemptDto.ProctorFrameRes(personCount, gazeDirection, true,
                violationRes.violationCount(), violationRes.maxViolations(), violationRes.shouldAutoSubmit());
    }

    /** UC-ANTICHEAT — video bằng chứng (màn hình + webcam ghép cạnh nhau, canvas-composite +
     * MediaRecorder phía FE), upload lúc nộp bài xong. Cảnh báo AI chỉ là marker hỗ trợ; video
     * mới là bằng chứng cuối cùng giảng viên xem lại khi có tranh chấp.
     *
     * <p>Key lưu trữ {@code "proctoring/" + attemptId + "/" + UUID + ".webm"} — đúng quy ước đặt
     * tên hiện có của dự án (opaque, KHÔNG bao giờ nhúng tiêu đề bài thi/ngày giờ vào tên file);
     * màn hình giám sát hiển thị tên/ngày giờ từ dữ liệu DB (join qua attempt), không suy ra từ
     * filename. */
    @Transactional
    public void uploadRecording(String studentEmail, Long attemptId, MultipartFile file, Integer durationSec) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getUser().getEmail().equals(studentEmail)) {
            throw new AccessDeniedDomainException("Ban khong co quyen tai video cho bai thi nay");
        }
        if (!Boolean.TRUE.equals(attempt.getQuiz().getIsProctored())) {
            throw new AccessDeniedDomainException("Bai thi nay khong bat giam sat AI");
        }

        String detectedMime;
        try {
            detectedMime = tika.detect(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new com.lms.common.exception.InvalidRequestException("Khong doc duoc file video: " + e.getMessage());
        }
        if (!detectedMime.startsWith("video/")) {
            throw new com.lms.common.exception.InvalidRequestException("File tai len phai la video, nhan duoc: " + detectedMime);
        }

        String key = "proctoring/" + attemptId + "/" + UUID.randomUUID() + ".webm";
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new com.lms.common.exception.InvalidRequestException("Khong tai duoc video len kho luu tru: " + e.getMessage());
        }

        ProctoringRecording recording = proctoringRecordingRepository.findByAttempt_Id(attemptId)
                .orElseGet(ProctoringRecording::new);
        recording.setAttempt(attempt);
        recording.setVideoUrl(url);
        recording.setDurationSec(durationSec);
        proctoringRecordingRepository.save(recording);
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
        
        return new QuizAttemptDto.SubmitRes(attemptId, attempt.getScore(), attempt.getCorrectCount(), attempt.getTotalQuestions(), details,
                Boolean.TRUE.equals(attempt.getQuiz().getIsDeleted()), attempt.getAiRiskLevel(), attempt.getAiRiskExplanation());
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
