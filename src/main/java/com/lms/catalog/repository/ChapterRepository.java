package com.lms.catalog.repository;

import com.lms.catalog.entity.Chapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Chapter}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    long countByCourseId(Long courseId);

    List<Chapter> findByCourseIdOrderByDisplayOrderAsc(Long courseId);
}
