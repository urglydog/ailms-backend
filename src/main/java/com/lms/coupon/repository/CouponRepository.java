package com.lms.coupon.repository;

import com.lms.coupon.entity.Coupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link Coupon} (15/09/2026, mở rộng ngoài đặc tả gốc). */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** BR-COUPON-04 — ứng viên hiển thị giá đã giảm tự động trên thẻ khóa học, lọc thêm theo
     * thời hạn/phạm vi/hạn mức ở tầng service (số lượng coupon nhỏ, không cần JPQL phức tạp). */
    List<Coupon> findByAutoApplyTrueAndIsActiveTrue();

    List<Coupon> findByCreatedBy_Id(Long userId);
}
