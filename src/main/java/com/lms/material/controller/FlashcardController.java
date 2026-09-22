package com.lms.material.controller;

import com.lms.material.dto.FlashcardDto;
import com.lms.material.service.FlashcardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

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

    /** Update flashcard content (frontText/backText). Only owner can edit. */
    @PatchMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> updateFlashcard(
            Principal principal,
            @PathVariable Long id,
            @RequestBody FlashcardDto.UpdateReq req) {
        flashcardService.updateFlashcard(principal.getName(), id, req);
        return ResponseEntity.ok(java.util.Map.of("message", "Updated successfully"));
    }

    /** Delete flashcard. Only owner can delete. */
    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> deleteFlashcard(
            Principal principal,
            @PathVariable Long id) {
        flashcardService.deleteFlashcard(principal.getName(), id);
        return ResponseEntity.ok(java.util.Map.of("message", "Deleted successfully"));
    }

    /** Get all cards in a deck with SRS review state for study mode. */
    @GetMapping("/deck/{deckId}/study")
    public ResponseEntity<List<FlashcardDto.CardWithReview>> getDeckStudyCards(
            Principal principal,
            @PathVariable Long deckId) {
        return ResponseEntity.ok(flashcardService.getDeckCardsWithReview(principal.getName(), deckId));
    }

    /** Add a new flashcard to a deck by MaterialGeneration ID. */
    @PostMapping("/deck/{generationId}")
    public ResponseEntity<FlashcardDto.CardWithReview> addFlashcard(
            Principal principal,
            @PathVariable Long generationId,
            @RequestBody FlashcardDto.AddReq req) {
        return ResponseEntity.ok(flashcardService.addFlashcard(principal.getName(), generationId, req));
    }

    /** Task 4 — Import CSV hàng loạt flashcard (phía giảng viên). */
    @PostMapping(value = "/deck/{generationId}/import-csv", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FlashcardDto.ImportResultRes> importFlashcardsCsv(
            Principal principal,
            @PathVariable Long generationId,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(flashcardService.addFlashcardsFromCsv(principal.getName(), generationId, file));
    }

    /** UpComming_Plan.md B2 — Import ngược file .txt Anki/Quizlet vào bộ thẻ cá nhân (học viên). */
    @PostMapping(value = "/deck/{generationId}/import-txt", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FlashcardDto.ImportResultRes> importFlashcardsTxt(
            Principal principal,
            @PathVariable Long generationId,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(flashcardService.addFlashcardsFromTxt(principal.getName(), generationId, file));
    }
}
