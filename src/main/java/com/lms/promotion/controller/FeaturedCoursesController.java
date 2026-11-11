package com.lms.promotion.controller;

import com.lms.common.enums.PromotionPlacement;
import com.lms.promotion.dto.PromotionDto;
import com.lms.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kệ "Được đề xuất" (Giai đoạn E, 11/11/2026). CÔNG KHAI nhưng CHỈ ĐỌC và nằm sẵn dưới {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}
 * ("/api/v1/courses/**") nên KHÔNG mở thêm quyền nào. Đầu vào: {@code placement} (enum, sai → 400), {@code categorySlug} (mẫu cố định,
 * sai → 400), {@code size} (kẹp 1–24). Đường dẫn literal "/featured" luôn được Spring ưu tiên hơn "/{slug}" của trang chi tiết khóa.
 */
@RestController
@RequestMapping("/api/v1/courses/featured")
@RequiredArgsConstructor
public class FeaturedCoursesController {

    private final PromotionService promotionService;

    @GetMapping
    public ResponseEntity<PromotionDto.FeaturedRes> featured(
            @RequestParam(defaultValue = "HOME_FEATURED") PromotionPlacement placement,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(promotionService.featured(placement, categorySlug, size));
    }
}
