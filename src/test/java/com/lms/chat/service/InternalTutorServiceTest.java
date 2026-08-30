package com.lms.chat.service;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.InternalTutorDto.ContextRes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** UC30 mở rộng — `courseTitle`/`courseDescription` cần cho system prompt fallback Google
 * Search Grounding của AI Worker (xem app/services/tutor_service.py::_build_fallback_system_instruction). */
@ExtendWith(MockitoExtension.class)
class InternalTutorServiceTest {

    @Mock private LessonRepository lessonRepository;

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
}
