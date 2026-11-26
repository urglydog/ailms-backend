package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorSuggestionDto.SuggestionRes;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorSuggestionServiceTest {

    private static final String EMAIL = "s@lms.local";

    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private EnrollmentSecurity enrollmentSecurity;
    @InjectMocks private TutorSuggestionService service;

    private Lesson prev;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        User me = new User();
        me.setId(1L);
        Course course = new Course();
        course.setId(5L);
        Chapter ch = new Chapter();
        ch.setTitle("Chuong 1");
        ch.setCourse(course);
        prev = new Lesson();
        prev.setId(9L);
        prev.setTitle("Mo dau");
        prev.setChapter(ch);
        lesson = new Lesson();
        lesson.setId(10L);
        lesson.setTitle("Thi hien tai");
        lesson.setChapter(ch);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(me));
        lenient().when(lessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        lenient().when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(5L))
                .thenReturn(List.of(prev, lesson));
    }

    private static Object[] row(String text, long uid) {
        return new Object[] {text, uid};
    }

    @Test
    void noHistory_returnsFourTemplatesWithPreviousLesson() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(true);
        when(chatMessageRepository.findRecentQuestionsForLesson(eq(5L), eq(10L), anyInt())).thenReturn(List.of());

        List<SuggestionRes> res = service.suggestions(EMAIL, 10L);

        assertThat(res).hasSize(4).allMatch(s -> s.source().equals("TEMPLATE"));
        assertThat(res.get(3).key()).isEqualTo("relation");
        assertThat(res.get(3).params()).containsEntry("previous", "Mo dau");
    }

    @Test
    void popularNeedsTwoDistinctOtherUsers_andComesFirst() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(true);
        List<Object[]> rows = new ArrayList<>();
        rows.add(row("Thì hiện tại dùng khi nào?", 2));
        rows.add(row("thi hien tai dung khi nao", 3)); // same after normalise, other user
        rows.add(row("Câu riêng của một người thôi", 4)); // only 1 user -> hidden
        rows.add(row("Câu chính tôi hỏi nhiều lần", 1));
        rows.add(row("Câu chính tôi hỏi nhiều lần", 1)); // viewer -> excluded
        when(chatMessageRepository.findRecentQuestionsForLesson(eq(5L), eq(10L), anyInt())).thenReturn(rows);

        List<SuggestionRes> res = service.suggestions(EMAIL, 10L);

        assertThat(res).hasSize(4);
        assertThat(res.get(0).source()).isEqualTo("POPULAR");
        assertThat(res.get(0).text()).isEqualTo("Thì hiện tại dùng khi nào?");
        assertThat(res.subList(1, 4)).allMatch(s -> s.source().equals("TEMPLATE"));
        assertThat(res).noneMatch(s -> s.text().contains("riêng") || s.text().contains("chính tôi"));
    }

    @Test
    void notEnrolled_isDenied() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(false);
        assertThatThrownBy(() -> service.suggestions(EMAIL, 10L)).isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void firstLesson_usesChapterTemplate() {
        assertThat(TutorSuggestionService.templates(prev, null).get(3).key()).isEqualTo("chapter");
    }
}
