package com.lms.catalog.repository;

import com.lms.catalog.entity.Course;
import com.lms.common.enums.CourseStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    Optional<Course> findBySlugAndStatus(String slug, CourseStatus status);

    /**
     * UC09 — tìm kiếm công khai. Mọi filter đều optional qua {@code :param IS NULL OR ...}
     * để tránh tổ hợp bùng nổ số lượng derived-query method. Chỉ trả khóa PUBLISHED (BR-ROLE-03).
     */
    @Query("SELECT c FROM Course c WHERE c.status = com.lms.common.enums.CourseStatus.PUBLISHED "
            + "AND (:keyword IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categorySlug IS NULL OR c.category.slug = :categorySlug) "
            + "AND (:level IS NULL OR c.level = :level) "
            + "AND (:isFree IS NULL OR c.isFree = :isFree)")
    Page<Course> searchPublic(
            @Param("keyword") String keyword,
            @Param("categorySlug") String categorySlug,
            @Param("level") String level,
            @Param("isFree") Boolean isFree,
            Pageable pageable);
}
