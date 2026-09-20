package com.lms.community.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
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
                        chat.getUser().getId().equals(instructorId)
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
            String email, Long courseId, boolean onlyUnanswered, Pageable pageable) {
        Page<LessonChat> page = courseId != null
                ? chatRepository.findByParentIsNullAndLesson_Chapter_Course_IdAndLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(
                        courseId, email, pageable)
                : chatRepository.findByParentIsNullAndLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(
                        email, pageable);

        List<QuestionRes> mapped = page.getContent().stream()
                .map(this::toQuestionRes)
                .filter(q -> !onlyUnanswered || q.answerCount() == 0)
                .toList();
        return new org.springframework.data.domain.PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ThreadRes getThread(String email, String questionId) {
        LessonChat question = loadOwnedQuestion(email, questionId);
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
                q.getUserName(), q.getUser().getAvatarUrl(), q.getContent(), q.getCreatedAt(), answerCount, hasInstructorAnswer);
    }

    private AnswerRes toAnswerRes(LessonChat a, LessonChat question) {
        Long instructorId = question.getLesson().getChapter().getCourse().getInstructor().getId();
        return new AnswerRes(
                a.getId(), a.getUserName(), a.getUser().getAvatarUrl(), a.getContent(), a.getCreatedAt(),
                a.getUser().getId().equals(instructorId));
    }
}
