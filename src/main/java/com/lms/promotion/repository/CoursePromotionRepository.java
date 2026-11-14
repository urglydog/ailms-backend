package com.lms.promotion.repository;

import com.lms.common.enums.PromotionPlacement;
import com.lms.common.enums.PromotionStatus;
import com.lms.promotion.entity.CoursePromotion;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePromotionRepository extends JpaRepository<CoursePromotion, Long> {

    /** Khóa đang được quảng bá ở 1 vị trí: APPROVED, trong khung thời gian, khóa còn xuất bản và công khai. */
    @Query("SELECT p FROM CoursePromotion p JOIN FETCH p.course c "
            + "WHERE p.placement = :placement AND p.status = com.lms.common.enums.PromotionStatus.APPROVED "
            + "AND p.startAt <= :now AND p.endAt > :now "
            + "AND c.status = com.lms.common.enums.CourseStatus.PUBLISHED "
            + "AND c.visibility = com.lms.common.enums.CourseVisibility.PUBLIC "
            + "ORDER BY p.startAt DESC, p.id")
    List<CoursePromotion> findActive(@Param("placement") PromotionPlacement placement,
                                     @Param("now") LocalDateTime now, Pageable pageable);

    /** Như {@link #findActive} nhưng chỉ các khóa thuộc 1 danh mục (kệ đầu trang danh mục). */
    @Query("SELECT p FROM CoursePromotion p JOIN FETCH p.course c "
            + "WHERE p.placement = :placement AND p.status = com.lms.common.enums.PromotionStatus.APPROVED "
            + "AND p.startAt <= :now AND p.endAt > :now AND c.category.slug = :categorySlug "
            + "AND c.status = com.lms.common.enums.CourseStatus.PUBLISHED "
            + "AND c.visibility = com.lms.common.enums.CourseVisibility.PUBLIC "
            + "ORDER BY p.startAt DESC, p.id")
    List<CoursePromotion> findActiveInCategory(@Param("placement") PromotionPlacement placement,
                                               @Param("categorySlug") String categorySlug,
                                               @Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"course", "requestedBy"})
    List<CoursePromotion> findByRequestedBy_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"course", "requestedBy"})
    List<CoursePromotion> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"course", "requestedBy"})
    List<CoursePromotion> findByStatusOrderByCreatedAtDesc(PromotionStatus status);

    /** Đã có đăng ký đang chờ/đang chạy cho cùng khóa + vị trí. */
    boolean existsByCourse_IdAndPlacementAndStatusIn(Long courseId, PromotionPlacement placement, Collection<PromotionStatus> statuses);
}
