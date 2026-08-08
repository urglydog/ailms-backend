package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Không có {@code ON DELETE CASCADE} ở tầng DB (Chapter/Course không giữ collection con — thiết
 * kế có chủ đích) nên xoá chương PHẢI tự xoá bài học bên trong trước, nếu không sẽ vỡ ràng buộc
 * khoá ngoại {@code fk_lessons_chapter_id}. Test này khoá lại đúng thứ tự cascade đó.
 */
@ExtendWith(MockitoExtension.class)
class ChapterServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private ChapterRepository chapterRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonService lessonService;

    @InjectMocks
    private ChapterService chapterService;

    private Chapter chapter;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail(OWNER_EMAIL);
        Course course = new Course();
        course.setInstructor(instructor);

        chapter = new Chapter();
        chapter.setId(20L);
        chapter.setCourse(course);

        when(chapterRepository.findById(20L)).thenReturn(Optional.of(chapter));
    }

    @Test
    void delete_cascadesEachLessonBeforeDeletingChapter() {
        Lesson lesson1 = new Lesson();
        lesson1.setId(1L);
        Lesson lesson2 = new Lesson();
        lesson2.setId(2L);
        when(lessonRepository.findByChapterIdOrderByDisplayOrderAsc(20L)).thenReturn(List.of(lesson1, lesson2));

        chapterService.delete(OWNER_EMAIL, 20L);

        verify(lessonService).deleteCascade(lesson1);
        verify(lessonService).deleteCascade(lesson2);
        verify(chapterRepository).delete(chapter);
    }

    @Test
    void delete_throwsWhenCallerIsNotOwner() {
        assertThatThrownBy(() -> chapterService.delete("khac@lms.local", 20L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
