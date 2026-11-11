package com.lms.promotion.controller;

import com.lms.common.enums.PromotionStatus;
import com.lms.promotion.dto.PromotionDto;
import com.lms.promotion.service.PromotionService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin duyệt/từ chối/kết thúc đợt quảng bá (Giai đoạn E, 11/11/2026). Chỉ ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public ResponseEntity<List<PromotionDto.Res>> list(@RequestParam(required = false) PromotionStatus status) {
        return ResponseEntity.ok(promotionService.listForAdmin(status));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PromotionDto.Res> approve(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PromotionDto.ApproveReq req) {
        return ResponseEntity.ok(promotionService.approve(principal.getName(), id, req));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PromotionDto.Res> reject(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PromotionDto.ReasonReq req) {
        return ResponseEntity.ok(promotionService.reject(principal.getName(), id, req.reason()));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<PromotionDto.Res> end(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(promotionService.end(principal.getName(), id));
    }
}
