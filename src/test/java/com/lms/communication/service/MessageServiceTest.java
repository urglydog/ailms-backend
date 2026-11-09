package com.lms.communication.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import com.lms.communication.dto.MessageDto.ConversationRes;
import com.lms.communication.dto.MessageDto.MessageRes;
import com.lms.communication.dto.MessageDto.StartReq;
import com.lms.communication.entity.Conversation;
import com.lms.communication.repository.ConversationRepository;
import com.lms.communication.repository.MessageRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Giao tiếp > Tin nhắn" (19/09/2026) — kiểm tra điều kiện ghi danh trước khi cho bắt đầu hội
 * thoại, và quyền tham gia trước khi gửi/xem tin nhắn. */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final String STUDENT_EMAIL = "hocvien@lms.local";
    private static final String INSTRUCTOR_EMAIL = "giangvien@lms.local";

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    private User student;
    private User instructor;
    private Course course;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setEmail(STUDENT_EMAIL);
        student.setFullName("Học viên A");

        instructor = new User();
        instructor.setId(2L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setFullName("Giảng viên B");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa Java");
        course.setInstructor(instructor);
    }

    @Test
    void startFromStudent_enrolled_createsConversation() {
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);
        when(conversationRepository.findByStudent_IdAndInstructor_Id(1L, 2L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        ConversationRes res = messageService.startFromStudent(STUDENT_EMAIL, new StartReq(10L, null));

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.otherUserName()).isEqualTo("Giảng viên B");
    }

    @Test
    void startFromStudent_notEnrolled_throwsAccessDenied() {
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.startFromStudent(STUDENT_EMAIL, new StartReq(10L, null)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void startFromInstructor_studentNotEnrolledInGivenCourse_throwsAccessDenied() {
        when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.startFromInstructor(INSTRUCTOR_EMAIL, new StartReq(10L, 1L)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void startFromInstructor_noCourseId_checksEnrollmentWithAnyOfInstructorCourses() {
        when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Instructor_Email(1L, INSTRUCTOR_EMAIL)).thenReturn(true);
        when(conversationRepository.findByStudent_IdAndInstructor_Id(1L, 2L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        ConversationRes res = messageService.startFromInstructor(INSTRUCTOR_EMAIL, new StartReq(null, 1L));

        assertThat(res.id()).isEqualTo(101L);
    }

    @Test
    void sendMessage_blankContent_throwsInvalidRequest() {
        assertThatThrownBy(() -> messageService.sendMessage(STUDENT_EMAIL, 100L, "   "))
                .isInstanceOf(InvalidRequestException.class);
        verify(conversationRepository, never()).findById(anyLong());
    }

    @Test
    void sendMessage_notParticipant_throwsAccessDenied() {
        Conversation conversation = conversationOf();
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> messageService.sendMessage("laxa@lms.local", 100L, "Chào"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void sendMessage_fromStudent_notifiesInstructorAndBumpsLastMessageAt() {
        Conversation conversation = conversationOf();
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            var m = inv.getArgument(0, com.lms.communication.entity.Message.class);
            m.setId(500L);
            m.setCreatedAt(java.time.LocalDateTime.now());
            return m;
        });

        MessageRes res = messageService.sendMessage(STUDENT_EMAIL, 100L, "Em có câu hỏi ạ");

        assertThat(res.senderName()).isEqualTo("Học viên A");
        assertThat(res.mine()).isTrue();
        verify(notificationService).notify(eq(2L), eq("NEW_MESSAGE"), anyString(), anyString(), anyString());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void getMessages_marksConversationReadForCurrentUser() {
        Conversation conversation = conversationOf();
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        lenient().when(messageRepository.findByConversation_IdOrderByCreatedAtAsc(100L)).thenReturn(java.util.List.of());

        messageService.getMessages(INSTRUCTOR_EMAIL, 100L);

        verify(messageRepository).markConversationRead(100L, 2L);
    }

    private Conversation conversationOf() {
        Conversation c = new Conversation();
        c.setId(100L);
        c.setStudent(student);
        c.setInstructor(instructor);
        c.setCourse(course);
        c.setLastMessageAt(java.time.LocalDateTime.now());
        return c;
    }
}
