package com.lms.catalog.repository;

import com.lms.catalog.entity.LessonDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link LessonDocument} (Giai đoạn 4, UC35). */
@Repository
public interface LessonDocumentRepository extends JpaRepository<LessonDocument, Long> {

    /** BR-UPLOAD-01: tối đa 5 file/bài học. */
    long countByLesson_Id(Long lessonId);

    List<LessonDocument> findByLesson_IdOrderByIdAsc(Long lessonId);
}
