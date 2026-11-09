package com.lms.chat.service;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.InternalTutorDto.ContextRes;
import com.lms.chat.dto.InternalTutorDto.CourseLessonRes;
import com.lms.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** UC30 mở rộng — `courseTitle`/`courseDescription` cần cho system prompt fallback Google
 * Search Grounding của AI Worker (xem app/services/tutor_service.py::_build_fallback_system_instruction).
 * (06/09/2026) — thêm {@code getCourseLessons}, dùng cho AI Worker tự phân loại bài học được
 * nhắc tới trong câu hỏi (nếu có) khác bài đang mở. */
@ExtendWith(MockitoExtension.class)
class InternalTutorServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks
    private InternalTutorService internalTutorService;

    @Test
    void getContext_includesCourseTitleAndDescription() {
        Course course = new Course();
        course.setTitle("Unity co ban");
        course.setDescription("Khoa hoc lam game voi Unity");

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        Lesson lesson = new Lesson();
        lesson.setId(21L);
        lesson.setTitle("Bai 1: Unity AI Assistant");
        lesson.setSourceLanguage("en-US");
        lesson.setDurationSec(600);
        lesson.setChapter(chapter);

        when(lessonRepository.findById(21L)).thenReturn(Optional.of(lesson));

        ContextRes ctx = internalTutorService.getContext(21L);

        assertThat(ctx.lessonTitle()).isEqualTo("Bai 1: Unity AI Assistant");
        assertThat(ctx.courseTitle()).isEqualTo("Unity co ban");
        assertThat(ctx.courseDescription()).isEqualTo("Khoa hoc lam game voi Unity");
    }

    @Test
    void getCourseLessons_returnsAllLessonsAcrossChaptersInDisplayOrder() {
        Chapter chapter1 = new Chapter();
        chapter1.setId(1L);
        Chapter chapter2 = new Chapter();
        chapter2.setId(2L);

        Lesson lesson1 = new Lesson();
        lesson1.setId(21L);
        lesson1.setTitle("Bai 1");
        lesson1.setDisplayOrder(1);
        Lesson lesson2 = new Lesson();
        lesson2.setId(22L);
        lesson2.setTitle("Bai 2");
        lesson2.setDisplayOrder(2);

        when(courseRepository.existsById(9L)).thenReturn(true);
        when(chapterRepository.findByCourseIdOrderByDisplayOrderAsc(9L)).thenReturn(List.of(chapter1, chapter2));
        when(lessonRepository.findByChapterIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(lesson1));
        when(lessonRepository.findByChapterIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of(lesson2));

        List<CourseLessonRes> result = internalTutorService.getCourseLessons(9L);

        assertThat(result).extracting(CourseLessonRes::lessonId).containsExactly(21L, 22L);
        assertThat(result).extracting(CourseLessonRes::lessonTitle).containsExactly("Bai 1", "Bai 2");
    }

    @Test
    void getCourseLessons_courseNotFound_throws() {
        when(courseRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> internalTutorService.getCourseLessons(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
