package com.lms.catalog.repository;

import com.lms.catalog.entity.LessonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link LessonDocument}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface LessonDocumentRepository extends JpaRepository<LessonDocument, Long> {
}
