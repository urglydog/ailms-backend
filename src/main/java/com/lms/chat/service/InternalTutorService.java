package com.lms.chat.service;

import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.InternalTutorDto.ContextRes;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC30 — ngữ cảnh bài học cho AI Worker dựng prompt Socratic Tutor. */
@Service
@RequiredArgsConstructor
public class InternalTutorService {

    private final LessonRepository lessonRepository;

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
}
