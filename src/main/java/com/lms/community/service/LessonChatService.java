package com.lms.community.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.community.dto.ChatMessageDto;
import com.lms.community.dto.LessonQaDto.AnswerRes;
import com.lms.community.dto.LessonQaDto.QuestionRes;
import com.lms.community.dto.LessonQaDto.ThreadRes;
import com.lms.community.entity.LessonChat;
import com.lms.community.repository.LessonChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonChatService {

    private final LessonChatRepository chatRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    /** Tên hiển thị THẬT của người gửi (không tin tên client gửi lên); thiếu thì dùng {@code fallback}. */
    @Transactional(readOnly = true)
    public String displayNameOf(Long userId, String fallback) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatHistory(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        Long instructorId = lesson.getChapter().getCourse().getInstructor().getId();
        return chatRepository.findByLessonIdOrderByCreatedAtAsc(lessonId).stream()
                .map(chat -> new ChatMessageDto(
                        chat.getId(),
                        chat.getUser().getId().toString(),
                        chat.getUserName(),
                        chat.getUser().getAvatarUrl(),
                        chat.getContent(),
                        chat.getCreatedAt().toString(),
                        chat.getParent() != null ? chat.getParent().getId() : null,
                        chat.getUser().getId().equals(instructorId),
                        chat.getParent() == null ? chat.getBestAnswerId() : null
                ))
                .toList();
    }

    /** (20/09/2026, sửa lỗi) — trả về {@link ChatMessageDto} ĐÃ LƯU (id/timestamp/isInstructor
     * thật từ DB) để {@code WebSocketChatController} phát lại ĐÚNG dữ liệu đã persist, thay vì
     * echo nguyên văn payload thô client gửi lên (trước đây khiến id tin nhắn broadcast ra sai —
     * xem docblock {@link ChatMessageDto}). */
    @Transactional
    public ChatMessageDto saveMessage(Long lessonId, Long userId, String userName, String content, String parentId) {
        Lesson lesson = lessonRepository.getReferenceById(lessonId);
        User user = userRepository.getReferenceById(userId);

        LessonChat chat = new LessonChat();
        chat.setLesson(lesson);
        chat.setUser(user);
        chat.setUserName(userName);
        chat.setContent(content);

        if (parentId != null && !parentId.isBlank()) {
            LessonChat parent = chatRepository.findById(parentId).orElse(null);
            chat.setParent(parent);
            // Có trả lời mới từ người khác giảng viên -> câu hỏi lại thành "Chưa đọc" với giảng viên.
            if (parent != null && !userId.equals(lesson.getChapter().getCourse().getInstructor().getId())) {
                parent.setInstructorReadAt(null);
            }
        }

        LessonChat saved = chatRepository.save(chat);
        Long instructorId = lesson.getChapter().getCourse().getInstructor().getId();
        return new ChatMessageDto(
                saved.getId(), userId.toString(), userName, user.getAvatarUrl(), content,
                saved.getCreatedAt().toString(), parentId, userId.equals(instructorId));
    }

    // ==================== "Giao tiếp > Hỏi đáp" của Giảng viên (19/09/2026) ====================
    // Tái dùng `lesson_chats` có sẵn thay vì tạo hẳn 1 mô hình Question/Answer song song: tin
    // GỐC (parent == null) coi là "câu hỏi", tin trả lời (có parent) là "câu trả lời" — học viên
    // vẫn thấy đúng luồng hội thoại quen thuộc ở tab "Hỏi đáp" của trang học bài, Giảng viên có
    // thêm 1 hộp thư gộp tất cả câu hỏi từ MỌI khóa của mình để không phải mở từng bài học.

    @Transactional(readOnly = true)
    public Page<QuestionRes> listQuestionsForInstructor(
            String email, Long courseId, boolean onlyUnanswered, String search, Pageable pageable) {
        return listQuestionsForInstructor(email, courseId, onlyUnanswered, false, search, pageable);
    }

    /** {@code noInstructorAnswer} — "Giảng viên chưa trả lời" (26/11/2026): chưa có trả lời nào của chính giảng viên khoá. */
    @Transactional(readOnly = true)
    public Page<QuestionRes> listQuestionsForInstructor(
            String email, Long courseId, boolean onlyUnanswered, boolean noInstructorAnswer, String search, Pageable pageable) {
        return listQuestionsForInstructor(email, new QaFilter(courseId, onlyUnanswered, noInstructorAnswer, false, false, search, "newest"), pageable);
    }

    /** Bộ lọc hộp thư Hỏi đáp (27/11/2026). {@code sort}: newest | oldest | most_replies. */
    public record QaFilter(Long courseId, boolean onlyUnanswered, boolean noInstructorAnswer,
                           boolean unread, boolean noBestAnswer, String search, String sort) {
    }

    @Transactional(readOnly = true)
    public Page<QuestionRes> listQuestionsForInstructor(String email, QaFilter f, Pageable pageable) {
        String kw = (f.search() == null || f.search().isBlank()) ? null : "%" + f.search().trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<LessonChat> page;
        if ("most_replies".equalsIgnoreCase(f.sort())) {
            Pageable unsorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            page = chatRepository.searchQuestionsForInstructorByReplies(email, f.courseId(), f.onlyUnanswered(),
                    f.noInstructorAnswer(), f.unread(), f.noBestAnswer(), kw, unsorted);
        } else {
            org.springframework.data.domain.Sort order = "oldest".equalsIgnoreCase(f.sort())
                    ? org.springframework.data.domain.Sort.by("createdAt").ascending()
                    : org.springframework.data.domain.Sort.by("createdAt").descending();
            Pageable sorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);
            page = chatRepository.searchQuestionsForInstructor(email, f.courseId(), f.onlyUnanswered(),
                    f.noInstructorAnswer(), f.unread(), f.noBestAnswer(), kw, sorted);
        }
        return page.map(this::toQuestionRes);
    }

    /** Mở câu hỏi = đánh dấu đã đọc (27/11/2026). */
    @Transactional
    public ThreadRes getThread(String email, String questionId) {
        LessonChat question = loadOwnedQuestion(email, questionId);
        if (question.getInstructorReadAt() == null) {
            question.setInstructorReadAt(java.time.Instant.now());
        }
        List<AnswerRes> answers = chatRepository.findByParent_IdOrderByCreatedAtAsc(questionId).stream()
                .map(a -> toAnswerRes(a, question))
                .toList();
        return new ThreadRes(toQuestionRes(question), answers);
    }

    @Transactional
    public void postInstructorReply(String email, String questionId, String content) {
        LessonChat question = loadOwnedQuestion(email, questionId);
        User instructor = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        LessonChat reply = new LessonChat();
        reply.setLesson(question.getLesson());
        reply.setUser(instructor);
        reply.setUserName(instructor.getFullName());
        reply.setContent(content);
        reply.setParent(question);
        chatRepository.save(reply);

        // Phát lại qua WebSocket để học viên đang mở tab "Hỏi đáp" của bài học thấy câu trả lời
        // ngay lập tức, giống hệt luồng gửi trực tiếp từ `WebSocketChatController`.
        ChatMessageDto broadcast = new ChatMessageDto(
                reply.getId(), instructor.getId().toString(), instructor.getFullName(), instructor.getAvatarUrl(),
                content, reply.getCreatedAt().toString(), question.getId(), true);
        messagingTemplate.convertAndSend("/topic/lesson/" + question.getLesson().getId() + "/chat", broadcast);

        String linkUrl = "/learn/" + question.getLesson().getId() + "?tab=qna";
        notificationService.notify(question.getUser().getId(), "QNA_REPLY",
                "Giảng viên đã trả lời câu hỏi của bạn",
                content,
                linkUrl);
    }

    /** Đánh dấu / bỏ đánh dấu đã đọc thủ công từ hộp thư (27/11/2026). */
    @Transactional
    public void setRead(String email, String questionId, boolean read) {
        LessonChat question = loadOwnedQuestion(email, questionId);
        question.setInstructorReadAt(read ? java.time.Instant.now() : null);
    }

    /**
     * Câu trả lời hay nhất (27/11/2026): chỉ NGƯỜI HỎI hoặc GIẢNG VIÊN sở hữu khoá được đặt/bỏ.
     * {@code answerId} null = bỏ đánh dấu; phải là trả lời TRỰC TIẾP của câu hỏi. Phát lại qua
     * WebSocket để tab "Hỏi đáp" đang mở cập nhật huy hiệu ngay.
     */
    @Transactional
    public void setBestAnswer(String email, String questionId, String answerId) {
        LessonChat question = chatRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("LessonChat", questionId));
        if (question.getParent() != null) {
            throw new com.lms.common.exception.InvalidRequestException("Chỉ câu hỏi gốc mới có câu trả lời hay nhất");
        }
        User instructor = question.getLesson().getChapter().getCourse().getInstructor();
        boolean isOwner = email.equals(instructor.getEmail());
        boolean isAuthor = email.equals(question.getUser().getEmail());
        if (!isOwner && !isAuthor) {
            throw new AccessDeniedDomainException("Chỉ người hỏi hoặc giảng viên mới được chọn câu trả lời hay nhất");
        }
        if (answerId != null && !answerId.isBlank()) {
            LessonChat answer = chatRepository.findById(answerId)
                    .orElseThrow(() -> new ResourceNotFoundException("LessonChat", answerId));
            if (answer.getParent() == null || !answer.getParent().getId().equals(questionId)) {
                throw new com.lms.common.exception.InvalidRequestException("Câu trả lời không thuộc câu hỏi này");
            }
            question.setBestAnswerId(answerId);
        } else {
            question.setBestAnswerId(null);
        }
        messagingTemplate.convertAndSend("/topic/lesson/" + question.getLesson().getId() + "/best-answer",
                java.util.Map.of("questionId", questionId,
                        "bestAnswerId", question.getBestAnswerId() == null ? "" : question.getBestAnswerId()));
    }

    private LessonChat loadOwnedQuestion(String email, String questionId) {
        LessonChat question = chatRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("LessonChat", questionId));
        if (!question.getLesson().getChapter().getCourse().getInstructor().getEmail().equals(email)) {
            throw new AccessDeniedDomainException("Bạn không có quyền xem câu hỏi này");
        }
        return question;
    }

    private QuestionRes toQuestionRes(LessonChat q) {
        long answerCount = chatRepository.countByParent_Id(q.getId());
        Long instructorId = q.getLesson().getChapter().getCourse().getInstructor().getId();
        boolean hasInstructorAnswer = answerCount > 0 && chatRepository.findByParent_IdOrderByCreatedAtAsc(q.getId())
                .stream().anyMatch(a -> a.getUser().getId().equals(instructorId));
        return new QuestionRes(
                q.getId(), q.getLesson().getId(), q.getLesson().getTitle(),
                q.getLesson().getChapter().getCourse().getId(), q.getLesson().getChapter().getCourse().getTitle(),
                q.getUserName(), q.getUser().getAvatarUrl(), q.getContent(), q.getCreatedAt(), answerCount, hasInstructorAnswer,
                q.getBestAnswerId(), q.getInstructorReadAt() != null);
    }

    private AnswerRes toAnswerRes(LessonChat a, LessonChat question) {
        Long instructorId = question.getLesson().getChapter().getCourse().getInstructor().getId();
        return new AnswerRes(
                a.getId(), a.getUserName(), a.getUser().getAvatarUrl(), a.getContent(), a.getCreatedAt(),
                a.getUser().getId().equals(instructorId), a.getId().equals(question.getBestAnswerId()));
    }
}
