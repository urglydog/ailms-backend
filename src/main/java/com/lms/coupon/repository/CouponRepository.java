package com.lms.coupon.repository;

import com.lms.coupon.entity.Coupon;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link Coupon} (15/09/2026, mở rộng ngoài đặc tả gốc). */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** BUG THẬT (03/10/2026) — chống race condition hạn mức coupon: 2 request tạo Payment
     * PENDING đồng thời cùng coupon đều đọc {@code used < maxUsage} là true (chưa ai PAID)
     * rồi cùng được tạo, bypass hạn mức "1 lượt/người". Lock dòng Coupon khi tái kiểm tra
     * hạn mức ngay trước khi lưu Payment để serialize hoá các request đồng thời — xem
     * {@code CouponService.reserveUsage}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> lockForUpdate(@Param("id") Long id);

    /** BR-COUPON-04 — ứng viên hiển thị giá đã giảm tự động trên thẻ khóa học, lọc thêm theo
     * thời hạn/phạm vi/hạn mức ở tầng service (số lượng coupon nhỏ, không cần JPQL phức tạp). */
    List<Coupon> findByAutoApplyTrueAndIsActiveTrue();

    List<Coupon> findByCreatedBy_Id(Long userId);
}
