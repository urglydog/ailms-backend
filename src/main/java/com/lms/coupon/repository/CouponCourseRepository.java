package com.lms.coupon.repository;

import com.lms.coupon.entity.CouponCourse;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link CouponCourse} (15/09/2026, mở rộng ngoài đặc tả gốc). */
@Repository
public interface CouponCourseRepository extends JpaRepository<CouponCourse, Long> {

    boolean existsByCoupon_IdAndCourse_Id(Long couponId, Long courseId);

    @EntityGraph(attributePaths = {"course"})
    List<CouponCourse> findByCoupon_Id(Long couponId);

    void deleteByCoupon_Id(Long couponId);
}
