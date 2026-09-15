package com.lms.coupon.controller;

import com.lms.coupon.dto.CouponDto.CreateReq;
import com.lms.coupon.dto.CouponDto.PreviewReq;
import com.lms.coupon.dto.CouponDto.PriceRes;
import com.lms.coupon.dto.CouponDto.Res;
import com.lms.coupon.dto.CouponDto.UpdateReq;
import com.lms.coupon.service.CouponService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Quản lý mã giảm giá kiểu Udemy (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57).
 * Admin quản lý coupon toàn hệ thống; Instructor chỉ tạo/sửa/xóa coupon cho khóa của chính
 * mình (kiểm ở tầng {@code CouponService}, không chỉ theo role — BR-COUPON-02/03).
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Res> create(Principal principal, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(couponService.create(principal.getName(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Res> update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(couponService.update(principal.getName(), id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        couponService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** Admin: toàn bộ coupon hệ thống. Instructor: chỉ coupon do chính mình tạo. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<Res>> listMine(Principal principal) {
        return ResponseEntity.ok(couponService.listMine(principal.getName()));
    }

    /** UC57 — xem trước giá sau khi nhập mã ở giỏ hàng/thanh toán, không tạo giao dịch. */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<PriceRes> preview(Principal principal, @Valid @RequestBody PreviewReq req) {
        return ResponseEntity.ok(couponService.previewPrice(principal.getName(), req.courseId(), req.code()));
    }
}
