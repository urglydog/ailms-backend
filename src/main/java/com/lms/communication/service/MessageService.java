package com.lms.communication.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.communication.dto.MessageDto.ConversationRes;
import com.lms.communication.dto.MessageDto.MessageRes;
import com.lms.communication.dto.MessageDto.StartReq;
import com.lms.communication.entity.Conversation;
import com.lms.communication.entity.Message;
import com.lms.communication.repository.ConversationRepository;
import com.lms.communication.repository.MessageRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Giao tiếp > Tin nhắn" (19/09/2026) — hộp thư 1-1 giữa học viên và giảng viên. Học viên chỉ
 * được bắt đầu hội thoại với giảng viên của 1 khóa mình ĐÃ ghi danh (tránh spam giảng viên lạ);
 * Giảng viên chỉ được bắt đầu với học viên đã ghi danh khóa của chính mình.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ConversationRes startFromStudent(String studentEmail, StartReq req) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));
        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), course.getId())) {
            throw new AccessDeniedDomainException("Bạn cần ghi danh khóa học này để nhắn tin cho giảng viên");
        }
        return toRes(findOrCreate(student, course.getInstructor(), course), student.getId());
    }

    @Transactional
    public ConversationRes startFromInstructor(String instructorEmail, StartReq req) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorEmail));
        User student = userRepository.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", req.studentId()));
        boolean enrolledInAnyOfMine = req.courseId() != null
                ? enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), req.courseId())
                : enrollmentRepository.existsByUser_IdAndCourse_Instructor_Email(student.getId(), instructorEmail);
        if (!enrolledInAnyOfMine) {
            throw new AccessDeniedDomainException("Học viên này chưa ghi danh khóa học đã chọn");
        }
        Course course = req.courseId() != null ? courseRepository.findById(req.courseId()).orElse(null) : null;
        if (course != null && !course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền nhắn tin trong ngữ cảnh khóa học này");
        }
        return toRes(findOrCreate(student, instructor, course), instructor.getId());
    }

    private Conversation findOrCreate(User student, User instructor, Course course) {
        return conversationRepository.findByStudent_IdAndInstructor_Id(student.getId(), instructor.getId())
                .orElseGet(() -> {
                    Conversation conversation = new Conversation();
                    conversation.setStudent(student);
                    conversation.setInstructor(instructor);
                    conversation.setCourse(course);
                    conversation.setLastMessageAt(LocalDateTime.now());
                    return conversationRepository.save(conversation);
                });
    }

    @Transactional(readOnly = true)
    public List<ConversationRes> listConversations(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        List<Conversation> asInstructor = conversationRepository.findByInstructor_EmailOrderByLastMessageAtDesc(email);
        List<Conversation> asStudent = conversationRepository.findByStudent_EmailOrderByLastMessageAtDesc(email);
        return java.util.stream.Stream.concat(asInstructor.stream(), asStudent.stream())
                .sorted((a, b) -> b.getLastMessageAt().compareTo(a.getLastMessageAt()))
                .map(c -> toRes(c, user.getId()))
                .toList();
    }

    @Transactional
    public List<MessageRes> getMessages(String email, Long conversationId) {
        Conversation conversation = loadParticipant(email, conversationId);
        User me = resolveSelf(email, conversation);
        messageRepository.markConversationRead(conversationId, me.getId());
        return messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new MessageRes(
                        m.getId(), m.getSender().getId(), m.getSender().getFullName(), m.getSender().getAvatarUrl(),
                        m.getContent(), m.getCreatedAt(), m.getSender().getId().equals(me.getId())))
                .toList();
    }

    @Transactional
    public MessageRes sendMessage(String email, Long conversationId, String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidRequestException("Nội dung tin nhắn không được để trống");
        }
        Conversation conversation = loadParticipant(email, conversationId);
        User sender = resolveSelf(email, conversation);
        User recipient = sender.getId().equals(conversation.getStudent().getId())
                ? conversation.getInstructor() : conversation.getStudent();

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content.trim());
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        notificationService.notify(
                recipient.getId(), "NEW_MESSAGE",
                "Tin nhắn mới từ " + sender.getFullName(), saved.getContent(), "/instructor/communication/messages");
        MessageRes res = new MessageRes(
                saved.getId(), sender.getId(), sender.getFullName(), sender.getAvatarUrl(),
                saved.getContent(), saved.getCreatedAt(), true);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, res);
        return res;
    }

    private Conversation loadParticipant(String email, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        if (!conversation.getStudent().getEmail().equals(email) && !conversation.getInstructor().getEmail().equals(email)) {
            throw new AccessDeniedDomainException("Bạn không có quyền xem hội thoại này");
        }
        return conversation;
    }

    private User resolveSelf(String email, Conversation conversation) {
        return conversation.getStudent().getEmail().equals(email) ? conversation.getStudent() : conversation.getInstructor();
    }

    private ConversationRes toRes(Conversation c, Long selfId) {
        boolean selfIsStudent = c.getStudent().getId().equals(selfId);
        User other = selfIsStudent ? c.getInstructor() : c.getStudent();
        String preview = messageRepository.findTopByConversation_IdOrderByCreatedAtDesc(c.getId())
                .map(Message::getContent).orElse(null);
        long unread = messageRepository.countByConversation_IdAndIsReadFalseAndSender_IdNot(c.getId(), selfId);
        return new ConversationRes(
                c.getId(), other.getId(), other.getFullName(), other.getAvatarUrl(),
                c.getCourse() != null ? c.getCourse().getId() : null,
                c.getCourse() != null ? c.getCourse().getTitle() : null,
                preview, c.getLastMessageAt(), unread);
    }
}
