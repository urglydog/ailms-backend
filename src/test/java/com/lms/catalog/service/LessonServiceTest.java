package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
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
import static org.mockito.Mockito.when;

/** Kiểm tra toggle Preview (không giới hạn số lượng) và BR-ROLE-01 (ownership). */
@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private LessonRepository lessonRepository;
    @Mock private ChapterRepository chapterRepository;

    @InjectMocks
    private LessonService lessonService;

    private Lesson lesson;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail(OWNER_EMAIL);

        Course course = new Course();
        course.setId(100L);
        course.setInstructor(instructor);

        Chapter chapter = new Chapter();
        chapter.setId(20L);
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(30L);
        lesson.setTitle("Bài 1");
        lesson.setChapter(chapter);
        lesson.setIsPreview(false);
        lesson.setStatus("READY");

        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));
        lenient().when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_canEnablePreviewWithNoQuantityLimit() {
        Res result = lessonService.update(OWNER_EMAIL, 30L, new UpdateReq("Bài 1", true));

        assertThat(result.isPreview()).isTrue();
    }

    @Test
    void update_canDisablePreview() {
        lesson.setIsPreview(true);

        Res result = lessonService.update(OWNER_EMAIL, 30L, new UpdateReq("Bài 1", false));

        assertThat(result.isPreview()).isFalse();
    }

    @Test
    void update_throwsWhenCallerIsNotOwner() {
        assertThatThrownBy(() -> lessonService.update("khac@lms.local", 30L, new UpdateReq("Bài 1", false)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
