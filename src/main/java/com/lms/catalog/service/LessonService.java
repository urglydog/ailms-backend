package com.lms.catalog.service;

import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý bài học — chỉ metadata ở F2.1 (UC33). Video được nạp riêng ở Giai đoạn 4 (UC34).
 * Mọi thao tác ghi kiểm ownership qua {@code lesson.getChapter().getCourse().getInstructor()}.
 */
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ChapterRepository chapterRepository;

    @Transactional
    public Res create(String instructorEmail, Long chapterId, CreateReq req) {
        Chapter chapter = loadOwnedChapter(chapterId, instructorEmail);

        Lesson lesson = new Lesson();
        lesson.setTitle(req.title());
        lesson.setChapter(chapter);
        lesson.setDisplayOrder((int) lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapterId).size());
        lesson.setStatus("DRAFT");
        lesson.setIsPreview(false);

        return mapToRes(lessonRepository.save(lesson));
    }

    @Transactional
    public Res update(String instructorEmail, Long lessonId, UpdateReq req) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);
        lesson.setTitle(req.title());
        lesson.setIsPreview(req.isPreview());
        return mapToRes(lessonRepository.save(lesson));
    }

    @Transactional
    public void delete(String instructorEmail, Long lessonId) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);
        lessonRepository.delete(lesson);
    }

    @Transactional
    public void reorder(String instructorEmail, Long chapterId, ReorderReq req) {
        loadOwnedChapter(chapterId, instructorEmail);
        List<Lesson> lessons = lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapterId);
        Map<Long, Lesson> byId = new HashMap<>();
        lessons.forEach(lesson -> byId.put(lesson.getId(), lesson));

        if (!byId.keySet().equals(new HashSet<>(req.orderedIds()))) {
            throw new InvalidRequestException("Danh sách thứ tự không khớp với các bài học hiện có");
        }

        for (int i = 0; i < req.orderedIds().size(); i++) {
            Lesson lesson = byId.get(req.orderedIds().get(i));
            lesson.setDisplayOrder(i);
            lessonRepository.save(lesson);
        }
    }

    private Chapter loadOwnedChapter(Long chapterId, String instructorEmail) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
        if (!chapter.getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên chương này");
        }
        return chapter;
    }

    private Lesson loadOwnedLesson(Long lessonId, String instructorEmail) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (!lesson.getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên bài học này");
        }
        return lesson;
    }

    private Res mapToRes(Lesson lesson) {
        return new Res(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getDisplayOrder(),
                lesson.getIsPreview(),
                lesson.getStatus(),
                lesson.getVideoSource(),
                lesson.getVideoUrl()
        );
    }
}
