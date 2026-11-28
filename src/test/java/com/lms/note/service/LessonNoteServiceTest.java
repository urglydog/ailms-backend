package com.lms.note.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.security.EnrollmentSecurity;
import com.lms.note.dto.LessonNoteDto.CreateReq;
import com.lms.note.dto.LessonNoteDto.NoteRes;
import com.lms.note.dto.LessonNoteDto.UpdateReq;
import com.lms.note.entity.LessonNote;
import com.lms.note.repository.LessonNoteRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonNoteServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private LessonNoteRepository noteRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentSecurity enrollmentSecurity;

    @InjectMocks private LessonNoteService service;

    private User user;
    private Course course;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        course = new Course();
        course.setId(5L);
        Chapter chapter = new Chapter();
        chapter.setCourse(course);
        lesson = new Lesson();
        lesson.setId(10L);
        lesson.setTitle("Bai 1");
        lesson.setChapter(chapter);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(lessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        lenient().when(noteRepository.save(any(LessonNote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LessonNote note(User owner) {
        LessonNote n = new LessonNote();
        n.setId(77L);
        n.setUser(owner);
        n.setLesson(lesson);
        n.setCourse(course);
        n.setTimestampSec(30);
        n.setContent("old");
        return n;
    }

    @Test
    void create_whenEnrolled_savesWithLessonCourseAndTrimmedContent() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(true);

        NoteRes res = service.create(EMAIL, 10L, new CreateReq(95, "  hello  "));

        assertThat(res.content()).isEqualTo("hello");
        assertThat(res.timestampSec()).isEqualTo(95);
        assertThat(res.lessonId()).isEqualTo(10L);
        assertThat(res.courseId()).isEqualTo(5L);
    }

    @Test
    void create_whenNotEnrolled_isDenied() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(false);

        assertThatThrownBy(() -> service.create(EMAIL, 10L, new CreateReq(0, "x")))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void list_courseScope_queriesByCourse() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(true);
        when(noteRepository.findByUser_IdAndCourse_IdOrderByLesson_IdAscTimestampSecAscIdAsc(1L, 5L))
                .thenReturn(List.of(note(user)));

        assertThat(service.list(EMAIL, 10L, "course")).hasSize(1);
        verify(noteRepository, never()).findByUser_IdAndLesson_IdOrderByTimestampSecAscIdAsc(any(), any());
    }

    @Test
    void list_defaultScope_queriesByLesson() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 10L, false)).thenReturn(true);
        when(noteRepository.findByUser_IdAndLesson_IdOrderByTimestampSecAscIdAsc(1L, 10L)).thenReturn(List.of());

        assertThat(service.list(EMAIL, 10L, "lesson")).isEmpty();
    }

    @Test
    void update_ownNote_changesContentAndTimestamp() {
        when(noteRepository.findById(77L)).thenReturn(Optional.of(note(user)));

        NoteRes res = service.update(EMAIL, 77L, new UpdateReq(12, "new"));

        assertThat(res.content()).isEqualTo("new");
        assertThat(res.timestampSec()).isEqualTo(12);
    }

    @Test
    void update_othersNote_isNotFound() {
        User other = new User();
        other.setId(2L);
        when(noteRepository.findById(77L)).thenReturn(Optional.of(note(other)));

        assertThatThrownBy(() -> service.update(EMAIL, 77L, new UpdateReq(null, "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_othersNote_isNotFoundAndKept() {
        User other = new User();
        other.setId(2L);
        when(noteRepository.findById(77L)).thenReturn(Optional.of(note(other)));

        assertThatThrownBy(() -> service.delete(EMAIL, 77L)).isInstanceOf(ResourceNotFoundException.class);
        verify(noteRepository, never()).delete(any(LessonNote.class));
    }
}
