package com.lms.community.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.service.NotificationService;
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
    @Mock private NotificationService notificationService;

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
    void listQuestionsForInstructor_passesFiltersAndSearchPatternToTheQuery() {
        LessonChat unanswered = questionOf("q2");
        PageRequest pageable = PageRequest.of(0, 20);
        when(chatRepository.searchQuestionsForInstructor(eq(INSTRUCTOR_EMAIL), eq(10L), eq(true), eq(false), eq(false), eq(false), eq("%an%"), any()))
                .thenReturn(new PageImpl<>(List.of(unanswered)));
        when(chatRepository.countByParent_Id("q2")).thenReturn(0L);

        org.springframework.data.domain.Page<QuestionRes> result =
                lessonChatService.listQuestionsForInstructor(INSTRUCTOR_EMAIL, 10L, true, "  An ", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("q2");
    }

    @Test
    void listQuestionsForInstructor_blankSearchMeansNoSearchFilter() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(chatRepository.searchQuestionsForInstructor(eq(INSTRUCTOR_EMAIL), eq(null), eq(false), eq(false), eq(false), eq(false), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(lessonChatService.listQuestionsForInstructor(INSTRUCTOR_EMAIL, null, false, "   ", pageable).getContent()).isEmpty();
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
        verify(notificationService).notify(
                eq(1L),
                eq("QNA_REPLY"),
                eq("Giảng viên đã trả lời câu hỏi của bạn"),
                eq("Cảm ơn em đã hỏi"),
                eq("/learn/100?tab=qna"));
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

    // ── Chưa đọc / câu trả lời hay nhất / sắp xếp (27/11/2026) ─────────────

    @Test
    void listQuestions_unreadNoBestAndMostReplies_useReplyCountQuery() {
        when(chatRepository.searchQuestionsForInstructorByReplies(eq(INSTRUCTOR_EMAIL), eq(null), eq(false), eq(false),
                eq(true), eq(true), eq(null), any())).thenReturn(new PageImpl<>(List.of(questionOf("q9"))));

        var result = lessonChatService.listQuestionsForInstructor(INSTRUCTOR_EMAIL,
                new LessonChatService.QaFilter(null, false, false, true, true, null, "most_replies"), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(QuestionRes::id).containsExactly("q9");
        assertThat(result.getContent().get(0).read()).isFalse();
    }

    @Test
    void listQuestions_oldestSortsByCreatedAtAscending() {
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        when(chatRepository.searchQuestionsForInstructor(anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), captor.capture())).thenReturn(new PageImpl<>(List.of()));

        lessonChatService.listQuestionsForInstructor(INSTRUCTOR_EMAIL,
                new LessonChatService.QaFilter(null, false, false, false, false, null, "oldest"), PageRequest.of(0, 20));

        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    void getThread_marksQuestionReadForInstructor() {
        LessonChat question = questionOf("q1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.findByParent_IdOrderByCreatedAtAsc("q1")).thenReturn(List.of());

        lessonChatService.getThread(INSTRUCTOR_EMAIL, "q1");

        assertThat(question.getInstructorReadAt()).isNotNull();
    }

    @Test
    void saveMessage_learnerReply_marksQuestionUnreadAgain() {
        User student = new User();
        student.setId(2L);
        LessonChat question = questionOf("q1");
        question.setInstructorReadAt(Instant.now());
        when(lessonRepository.getReferenceById(100L)).thenReturn(lesson);
        when(userRepository.getReferenceById(2L)).thenReturn(student);
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.save(any(LessonChat.class))).thenAnswer(inv -> inv.getArgument(0));

        lessonChatService.saveMessage(100L, 2L, "HV", "Em bổ sung", "q1");

        assertThat(question.getInstructorReadAt()).isNull();
    }

    @Test
    void setBestAnswer_byQuestionAuthor_setsAndBroadcasts() {
        User student = studentAuthor();
        LessonChat question = questionOf("q1");
        question.setUser(student);
        LessonChat answer = answerOf("a1", question);
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.findById("a1")).thenReturn(Optional.of(answer));

        lessonChatService.setBestAnswer("hv@lms.local", "q1", "a1");

        assertThat(question.getBestAnswerId()).isEqualTo("a1");
        verify(messagingTemplate).convertAndSend(eq("/topic/lesson/100/best-answer"), any(Object.class));
    }

    @Test
    void setBestAnswer_byInstructor_canUnmark() {
        LessonChat question = questionOf("q1");
        question.setUser(studentAuthor());
        question.setBestAnswerId("a1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));

        lessonChatService.setBestAnswer(INSTRUCTOR_EMAIL, "q1", null);

        assertThat(question.getBestAnswerId()).isNull();
    }

    @Test
    void setBestAnswer_byOtherLearner_isDenied() {
        LessonChat question = questionOf("q1");
        question.setUser(studentAuthor());
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> lessonChatService.setBestAnswer("khac@lms.local", "q1", "a1"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void setBestAnswer_answerOfAnotherQuestion_isRejected() {
        LessonChat question = questionOf("q1");
        LessonChat answer = answerOf("a2", questionOf("q2"));
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.findById("a2")).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> lessonChatService.setBestAnswer(INSTRUCTOR_EMAIL, "q1", "a2"))
                .isInstanceOf(com.lms.common.exception.InvalidRequestException.class);
        assertThat(question.getBestAnswerId()).isNull();
    }

    @Test
    void getThread_flagsBestAnswer() {
        LessonChat question = questionOf("q1");
        question.setBestAnswerId("a1");
        when(chatRepository.findById("q1")).thenReturn(Optional.of(question));
        when(chatRepository.findByParent_IdOrderByCreatedAtAsc("q1")).thenReturn(List.of(answerOf("a1", question), answerOf("a2", question)));

        ThreadRes thread = lessonChatService.getThread(INSTRUCTOR_EMAIL, "q1");

        assertThat(thread.answers()).extracting(a -> a.isBest()).containsExactly(true, false);
        assertThat(thread.question().bestAnswerId()).isEqualTo("a1");
    }

    private User studentAuthor() {
        User student = new User();
        student.setId(2L);
        student.setEmail("hv@lms.local");
        return student;
    }

    private LessonChat answerOf(String id, LessonChat parent) {
        LessonChat a = new LessonChat();
        a.setId(id);
        a.setParent(parent);
        a.setLesson(lesson);
        a.setUser(instructor);
        a.setUserName("Cô Lan");
        a.setContent("Trả lời " + id);
        a.setCreatedAt(Instant.now());
        return a;
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
