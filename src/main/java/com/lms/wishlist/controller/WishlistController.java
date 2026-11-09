package com.lms.wishlist.controller;

import com.lms.wishlist.dto.WishlistDto;
import com.lms.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Danh sách yêu thích (14/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc. */
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<List<WishlistDto.ItemRes>> getMyWishlist(Principal principal) {
        return ResponseEntity.ok(wishlistService.getMyWishlist(principal.getName()));
    }

    @PostMapping("/items")
    public ResponseEntity<WishlistDto.ItemRes> addItem(Principal principal, @Valid @RequestBody WishlistDto.AddReq req) {
        return ResponseEntity.ok(wishlistService.addItem(principal.getName(), req.courseId()));
    }

    @DeleteMapping("/items/{courseId}")
    public ResponseEntity<Void> removeItem(Principal principal, @PathVariable Long courseId) {
        wishlistService.removeItem(principal.getName(), courseId);
        return ResponseEntity.noContent().build();
    }
}
