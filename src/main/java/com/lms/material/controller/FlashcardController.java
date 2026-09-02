package com.lms.material.controller;

import com.lms.material.dto.FlashcardDto;
import com.lms.material.service.FlashcardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/flashcards")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    @PostMapping("/{id}/review")
    public ResponseEntity<FlashcardDto.ReviewRes> reviewCard(
            Principal principal,
            @PathVariable Long id,
            @RequestBody FlashcardDto.ReviewReq req) {
        return ResponseEntity.ok(flashcardService.reviewCard(principal.getName(), id, req));
    }
}
