package com.lms.chat.service;

import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.InternalTutorDto.ContextRes;
import com.lms.chat.dto.InternalTutorDto.CourseLessonRes;
import com.lms.common.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC30 — ngữ cảnh bài học/khóa học cho AI Worker dựng prompt Socratic Tutor. */
@Service
@RequiredArgsConstructor
public class InternalTutorService {

    private final LessonRepository lessonRepository;
    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public ContextRes getContext(Long lessonId) {
        if (lessonId != null && lessonId == -1L) {
            return new ContextRes("Bài tập trắc nghiệm", "vi", 0, "Hệ thống AI LMS", "Giải thích câu hỏi trắc nghiệm");
        }

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        var course = lesson.getChapter().getCourse();
        return new ContextRes(
                lesson.getTitle(), lesson.getSourceLanguage(), lesson.getDurationSec(),
                course.getTitle(), course.getDescription());
    }

    /** UC30 mở rộng (06/09/2026) — danh sách bài học của khóa (mọi chương, đúng thứ tự hiển
     * thị) để AI Worker tự phân loại bài học viên nhắc tới trong câu hỏi (nếu có) khác bài
     * đang mở — cùng khuôn truy vấn với {@code LessonPlayerService.toChapterNavRes}. */
    @Transactional(readOnly = true)
    public List<CourseLessonRes> getCourseLessons(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
        return chapterRepository.findByCourseIdOrderByDisplayOrderAsc(courseId).stream()
                .flatMap(chapter -> lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId()).stream())
                .map(lesson -> new CourseLessonRes(lesson.getId(), lesson.getTitle(), lesson.getDisplayOrder()))
                .toList();
    }
}
