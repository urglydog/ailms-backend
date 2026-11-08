package com.lms.catalog.repository;

import com.lms.catalog.entity.Course;
import com.lms.common.enums.CourseStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
     * UC09 — điều kiện lọc DÙNG CHUNG cho 3 truy vấn tìm kiếm công khai bên dưới (chỉ khác ORDER BY).
     * Mọi filter đều optional qua {@code :param IS NULL OR ...}. Chỉ khóa PUBLISHED + PUBLIC (BR-ROLE-03;
     * khóa "Riêng tư" chỉ vào được qua link trực tiếp).
     *
     * <p>Tổng thời lượng KHÔNG phải cột trên {@code Course} nên cộng dồn {@code Lesson.durationSec} qua
     * subquery tương quan — có index {@code idx_chapters_course_order}/{@code idx_lessons_chapter_order} hỗ trợ.
     */
    String PUBLIC_SEARCH_WHERE = "FROM Course c WHERE c.status = com.lms.common.enums.CourseStatus.PUBLISHED "
            + "AND c.visibility = com.lms.common.enums.CourseVisibility.PUBLIC "
            + "AND (:keyword IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categorySlug IS NULL OR c.category.slug = :categorySlug) "
            + "AND (:level IS NULL OR c.level = :level) "
            + "AND (:isFree IS NULL OR c.isFree = :isFree) "
            + "AND (:minRating IS NULL OR c.avgRating >= :minRating) "
            + "AND (:minDurationSec IS NULL OR "
            + "     (SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l WHERE l.chapter.course = c) >= :minDurationSec) "
            + "AND (:maxDurationSec IS NULL OR "
            + "     (SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l WHERE l.chapter.course = c) <= :maxDurationSec) ";

    /**
     * Sắp xếp theo cột có sẵn (Mới nhất, Đánh giá cao) — thứ tự lấy từ {@code pageable.getSort()}.
     * Trả {@link Slice} (08/11/2026): Kho khóa học tải dần khi cuộn, chỉ cần biết "còn trang sau không",
     * nên BỎ câu {@code COUNT(*)} mà {@code Page} luôn chạy kèm — đỡ một truy vấn nặng mỗi lần lọc.
     */
    @Query("SELECT c " + PUBLIC_SEARCH_WHERE)
    Slice<Course> searchPublic(
            @Param("keyword") String keyword,
            @Param("categorySlug") String categorySlug,
            @Param("level") String level,
            @Param("isFree") Boolean isFree,
            @Param("minRating") java.math.BigDecimal minRating,
            @Param("minDurationSec") Integer minDurationSec,
            @Param("maxDurationSec") Integer maxDurationSec,
            Pageable pageable);

    /** "Đánh giá nhiều": số đánh giá không lưu sẵn trên Course nên ORDER BY subquery đếm (bỏ đánh giá đã ẩn). */
    @Query("SELECT c " + PUBLIC_SEARCH_WHERE
            + "ORDER BY (SELECT COUNT(r) FROM CourseReview r WHERE r.course = c AND r.isHidden = false) DESC, "
            + "c.createdAt DESC, c.id DESC")
    Slice<Course> searchPublicByReviews(
            @Param("keyword") String keyword,
            @Param("categorySlug") String categorySlug,
            @Param("level") String level,
            @Param("isFree") Boolean isFree,
            @Param("minRating") java.math.BigDecimal minRating,
            @Param("minDurationSec") Integer minDurationSec,
            @Param("maxDurationSec") Integer maxDurationSec,
            Pageable pageable);

    /**
     * "Phù hợp nhất" (chỉ khi có từ khóa): tiêu đề BẮT ĐẦU bằng từ khóa xếp trước tiêu đề chỉ CHỨA từ
     * khóa, cùng hạng xếp theo tên. Trước đây sắp lại trong từng trang ở Java — sai thứ tự giữa các trang.
     */
    @Query("SELECT c " + PUBLIC_SEARCH_WHERE
            + "ORDER BY CASE WHEN LOWER(c.title) LIKE LOWER(CONCAT(:keyword, '%')) THEN 0 ELSE 1 END, c.title ASC, c.id ASC")
    Slice<Course> searchPublicByRelevance(
            @Param("keyword") String keyword,
            @Param("categorySlug") String categorySlug,
            @Param("level") String level,
            @Param("isFree") Boolean isFree,
            @Param("minRating") java.math.BigDecimal minRating,
            @Param("minDurationSec") Integer minDurationSec,
            @Param("maxDurationSec") Integer maxDurationSec,
            Pageable pageable);
}
