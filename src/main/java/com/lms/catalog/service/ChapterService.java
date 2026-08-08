package com.lms.catalog.service;

import com.lms.catalog.dto.ChapterDto.*;
import com.lms.catalog.dto.LessonDto;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý chương của khóa học (UC32). Mọi thao tác ghi đều kiểm ownership qua
 * {@code chapter.getCourse().getInstructor()} (Chapter không có field instructor riêng).
 */
@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonService lessonService;

    @Transactional
    public Res create(String instructorEmail, Long courseId, CreateReq req) {
        Course course = loadOwnedCourse(courseId, instructorEmail);

        Chapter chapter = new Chapter();
        chapter.setTitle(req.title());
        chapter.setCourse(course);
        chapter.setDisplayOrder((int) chapterRepository.countByCourseId(courseId));

        return mapToRes(chapterRepository.save(chapter));
    }

    @Transactional
    public Res update(String instructorEmail, Long chapterId, UpdateReq req) {
        Chapter chapter = loadOwnedChapter(chapterId, instructorEmail);
        chapter.setTitle(req.title());
        return mapToRes(chapterRepository.save(chapter));
    }

    /**
     * Không có {@code ON DELETE CASCADE} ở tầng DB (composition có chủ đích) nên phải tự xoá
     * bài học bên trong TRƯỚC (kèm dọn video/tài liệu trên B2 qua {@link LessonService#deleteCascade}),
     * nếu không sẽ vỡ ràng buộc khoá ngoại {@code fk_lessons_chapter_id}.
     */
    @Transactional
    public void delete(String instructorEmail, Long chapterId) {
        Chapter chapter = loadOwnedChapter(chapterId, instructorEmail);
        lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapterId).forEach(lessonService::deleteCascade);
        chapterRepository.delete(chapter);
    }

    @Transactional
    public void reorder(String instructorEmail, Long courseId, ReorderReq req) {
        loadOwnedCourse(courseId, instructorEmail);
        List<Chapter> chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAsc(courseId);
        Map<Long, Chapter> byId = new HashMap<>();
        chapters.forEach(chapter -> byId.put(chapter.getId(), chapter));

        if (!byId.keySet().equals(new java.util.HashSet<>(req.orderedIds()))) {
            throw new InvalidRequestException("Danh sách thứ tự không khớp với các chương hiện có");
        }

        for (int i = 0; i < req.orderedIds().size(); i++) {
            Chapter chapter = byId.get(req.orderedIds().get(i));
            chapter.setDisplayOrder(i);
            chapterRepository.save(chapter);
        }
    }

    private Course loadOwnedCourse(Long courseId, String instructorEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên khóa học này");
        }
        return course;
    }

    private Chapter loadOwnedChapter(Long chapterId, String instructorEmail) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
        if (!chapter.getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên chương này");
        }
        return chapter;
    }

    private Res mapToRes(Chapter chapter) {
        List<LessonDto.Res> lessons = lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId())
                .stream()
                .map(lesson -> new LessonDto.Res(
                        lesson.getId(),
                        lesson.getTitle(),
                        lesson.getDisplayOrder(),
                        lesson.getIsPreview(),
                        lesson.getStatus(),
                        lesson.getVideoSource(),
                        lesson.getVideoUrl(),
                        lesson.getYoutubeId(),
                        lesson.getDurationSec()
                ))
                .toList();
        return new Res(chapter.getId(), chapter.getTitle(), chapter.getDisplayOrder(), lessons);
    }
}
