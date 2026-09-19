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
     *
     * <p>{@code minDurationSec}/{@code maxDurationSec} (14/09/2026, mở rộng — bộ lọc "Video
     * Duration" kiểu Udemy): tổng thời lượng KHÔNG phải cột trên {@code Course}, phải cộng dồn
     * {@code Lesson.durationSec} qua subquery tương quan (correlated subquery) — chấp nhận được
     * ở quy mô hiện tại (danh mục mẫu nhỏ), cần đánh index/denormalize nếu catalog lớn hơn nhiều.
     */
    @Query("SELECT c FROM Course c WHERE c.status = com.lms.common.enums.CourseStatus.PUBLISHED "
            // (19/09/2026) — khóa "Riêng tư" (mời/mật khẩu) không hiện trong tìm kiếm/danh mục
            // công khai, chỉ truy cập được qua link trực tiếp (xem CoursePublicService.getBySlug).
            + "AND c.visibility = com.lms.common.enums.CourseVisibility.PUBLIC "
            + "AND (:keyword IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categorySlug IS NULL OR c.category.slug = :categorySlug) "
            + "AND (:level IS NULL OR c.level = :level) "
            + "AND (:isFree IS NULL OR c.isFree = :isFree) "
            + "AND (:minRating IS NULL OR c.avgRating >= :minRating) "
            + "AND (:minDurationSec IS NULL OR "
            + "     (SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l WHERE l.chapter.course = c) >= :minDurationSec) "
            + "AND (:maxDurationSec IS NULL OR "
            + "     (SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l WHERE l.chapter.course = c) <= :maxDurationSec)")
    Page<Course> searchPublic(
            @Param("keyword") String keyword,
            @Param("categorySlug") String categorySlug,
            @Param("level") String level,
            @Param("isFree") Boolean isFree,
            @Param("minRating") java.math.BigDecimal minRating,
            @Param("minDurationSec") Integer minDurationSec,
            @Param("maxDurationSec") Integer maxDurationSec,
            Pageable pageable);
}
