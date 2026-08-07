package com.lms.catalog.repository;

import com.lms.catalog.entity.Lesson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Lesson}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    long countByChapter_CourseId(Long courseId);

    /** BR-COURSE-01: chỉ bài học đã có video hợp lệ (status=READY, Giai đoạn 4) mới tính. */
    long countByChapter_CourseIdAndStatus(Long courseId, String status);

    List<Lesson> findByChapterIdOrderByDisplayOrderAsc(Long chapterId);
}
