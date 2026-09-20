package com.lms.community.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.community.dto.ChatMessageDto;
import com.lms.community.dto.LessonQaDto.QuestionRes;
import com.lms.community.dto.LessonQaDto.ThreadRes;
import com.lms.community.entity.LessonChat;
import com.lms.community.repository.LessonChatRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Giao tiếp > Hỏi đáp" của Giảng viên (19/09/2026) — tái dùng `lesson_chats`, tin GỐC là câu
 * hỏi, tin trả lời có `parent` trỏ tới câu hỏi đó. Kiểm tra lọc "chưa có câu trả lời", quyền sở
 * hữu khóa học, và trả lời của Giảng viên được phát lại qua WebSocket cho học viên thấy ngay. */
@ExtendWith(MockitoExtension.class)
class LessonChatServiceTest {

    private static final String INSTRUCTOR_EMAIL = "giangvien@lms.local";

    @Mock private LessonChatRepository chatRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private LessonChatService lessonChatService;

    private User instructor;
    private Course course;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setFullName("Cô Lan");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa Java");
        course.setInstructor(instructor);

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(100L);
        lesson.setTitle("Bài 1");
        lesson.setChapter(chapter);
    }

    @Test
    void listQuestionsForInstructor_onlyUnanswered_filtersOutQuestionsWithReplies() {
        LessonChat answered = questionOf("q1");
        LessonChat unanswered = questionOf("q2");
        PageRequest pageable = PageRequest.of(0, 20);
        when(chatRepository.findByParentIsNullAndLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(
                eq(INSTRUCTOR_EMAIL), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(answered, unanswered)));
        when(chatRepository.countByParent_Id("q1")).thenReturn(1L);
        when(chatRepository.countByParent_Id("q2")).thenReturn(0L);
        lenient().when(chatRepository.findByParent_IdOrderByCreatedAtAsc("q1")).thenReturn(List.of());

        org.springframework.data.domain.Page<QuestionRes> result =
                lessonChatService.listQuestionsForInstructor(INSTRUCTOR_EMAIL, null, true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("q2");
    }

    @Test
    void getThread_notOwner_throwsAccessDenied() {
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        LessonChat question = questionOf("q1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> lessonChatService.getThread(INSTRUCTOR_EMAIL, "q1"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void getThread_ownerCourse_returnsQuestionWithAnswers() {
        LessonChat question = questionOf("q1");
        LessonChat answer = new LessonChat();
        answer.setId("a1");
        answer.setParent(question);
        answer.setUser(instructor);
        answer.setUserName("Cô Lan");
        answer.setContent("Trả lời câu hỏi");
        answer.setCreatedAt(Instant.now());
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.findByParent_IdOrderByCreatedAtAsc("q1")).thenReturn(List.of(answer));
        lenient().when(chatRepository.countByParent_Id("q1")).thenReturn(1L);

        ThreadRes thread = lessonChatService.getThread(INSTRUCTOR_EMAIL, "q1");

        assertThat(thread.answers()).hasSize(1);
        assertThat(thread.answers().get(0).isInstructor()).isTrue();
    }

    @Test
    void postInstructorReply_notOwner_throwsAccessDenied() {
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        LessonChat question = questionOf("q1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> lessonChatService.postInstructorReply(INSTRUCTOR_EMAIL, "q1", "Trả lời"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void postInstructorReply_savesAndBroadcastsToLessonTopic() {
        LessonChat question = questionOf("q1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        when(chatRepository.save(any())).thenAnswer(inv -> {
            LessonChat saved = inv.getArgument(0);
            saved.setId("a1");
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        lessonChatService.postInstructorReply(INSTRUCTOR_EMAIL, "q1", "Cảm ơn em đã hỏi");

        verify(chatRepository).save(any(LessonChat.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/lesson/100/chat"), any(Object.class));
    }

    // ── getChatHistory / saveMessage (20/09/2026, sửa lỗi id/isInstructor) ─────────────

    @Test
    void getChatHistory_marksInstructorMessageCorrectly() {
        User student = new User();
        student.setId(2L);
        student.setFullName("Học viên X");

        LessonChat fromStudent = new LessonChat();
        fromStudent.setId("m1");
        fromStudent.setLesson(lesson);
        fromStudent.setUser(student);
        fromStudent.setUserName("Học viên X");
        fromStudent.setContent("Câu hỏi");
        fromStudent.setCreatedAt(Instant.now());

        LessonChat fromInstructor = new LessonChat();
        fromInstructor.setId("m2");
        fromInstructor.setLesson(lesson);
        fromInstructor.setUser(instructor);
        fromInstructor.setUserName("Cô Lan");
        fromInstructor.setContent("Trả lời");
        fromInstructor.setParent(fromStudent);
        fromInstructor.setCreatedAt(Instant.now());

        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        when(chatRepository.findByLessonIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(fromStudent, fromInstructor));

        List<ChatMessageDto> history = lessonChatService.getChatHistory(100L);

        assertThat(history).hasSize(2);
        ChatMessageDto studentMsg = history.get(0);
        assertThat(studentMsg.id()).isEqualTo("m1");
        assertThat(studentMsg.senderId()).isEqualTo("2");
        assertThat(studentMsg.isInstructor()).isFalse();
        ChatMessageDto instructorMsg = history.get(1);
        assertThat(instructorMsg.id()).isEqualTo("m2");
        assertThat(instructorMsg.senderId()).isEqualTo("1");
        assertThat(instructorMsg.isInstructor()).isTrue();
        assertThat(instructorMsg.parentId()).isEqualTo("m1");
    }

    @Test
    void saveMessage_returnsPersistedDtoWithRealIdAndInstructorFlag() {
        when(lessonRepository.getReferenceById(100L)).thenReturn(lesson);
        when(userRepository.getReferenceById(1L)).thenReturn(instructor);
        when(chatRepository.save(any(LessonChat.class))).thenAnswer(inv -> {
            LessonChat saved = inv.getArgument(0);
            saved.setId("new-msg-id");
            return saved;
        });

        ChatMessageDto result = lessonChatService.saveMessage(100L, 1L, "Cô Lan", "Nội dung mới", null);

        assertThat(result.id()).isEqualTo("new-msg-id");
        assertThat(result.senderId()).isEqualTo("1");
        assertThat(result.isInstructor()).isTrue();
    }

    private LessonChat questionOf(String id) {
        LessonChat q = new LessonChat();
        q.setId(id);
        q.setLesson(lesson);
        q.setUser(instructor);
        q.setUserName("Học viên X");
        q.setContent("Câu hỏi " + id);
        q.setCreatedAt(Instant.now());
        return q;
    }
}
