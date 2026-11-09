package com.lms.payment.controller;

import com.lms.payment.dto.CartDto;
import com.lms.payment.service.CartService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc. */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<List<CartDto.ItemRes>> getMyCart(Principal principal) {
        return ResponseEntity.ok(cartService.getMyCart(principal.getName()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto.ItemRes> addItem(Principal principal, @Valid @RequestBody CartDto.AddReq req) {
        return ResponseEntity.ok(cartService.addItem(principal.getName(), req.courseId()));
    }

    @DeleteMapping("/items/{courseId}")
    public ResponseEntity<Void> removeItem(Principal principal, @PathVariable Long courseId) {
        cartService.removeItem(principal.getName(), courseId);
        return ResponseEntity.noContent().build();
    }
}
