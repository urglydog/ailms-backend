package com.lms.promotion.controller;

import com.lms.promotion.dto.PromotionDto;
import com.lms.promotion.service.PromotionService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giảng viên đăng ký/hủy quảng bá khóa của mình (Giai đoạn E, 11/11/2026). Cần đăng nhập, chỉ INSTRUCTOR. */
@RestController
@RequestMapping("/api/v1/instructor/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public ResponseEntity<List<PromotionDto.Res>> mine(Principal principal) {
        return ResponseEntity.ok(promotionService.listMine(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<PromotionDto.Res> request(Principal principal, @Valid @RequestBody PromotionDto.CreateReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.request(principal.getName(), req));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PromotionDto.Res> cancel(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(promotionService.cancel(principal.getName(), id));
    }
}
