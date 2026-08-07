package com.lms.catalog.repository;

import com.lms.catalog.entity.Course;
import com.lms.common.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Course}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySlug(String slug);

    Page<Course> findByInstructor_Email(String email, Pageable pageable);

    Page<Course> findByInstructor_EmailAndStatus(String email, CourseStatus status, Pageable pageable);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    long countByStatus(CourseStatus status);

    long countByInstructor_Email(String email);
}
