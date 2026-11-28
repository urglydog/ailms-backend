package com.lms.note.repository;

import com.lms.note.entity.LessonNote;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonNoteRepository extends JpaRepository<LessonNote, Long> {

    @EntityGraph(attributePaths = {"lesson"})
    List<LessonNote> findByUser_IdAndLesson_IdOrderByTimestampSecAscIdAsc(Long userId, Long lessonId);

    @EntityGraph(attributePaths = {"lesson"})
    List<LessonNote> findByUser_IdAndCourse_IdOrderByLesson_IdAscTimestampSecAscIdAsc(Long userId, Long courseId);
}
