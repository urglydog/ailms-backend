package com.lms.material.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.dto.FlashcardDto;
import com.lms.material.entity.Flashcard;
import com.lms.material.entity.FlashcardReview;
import com.lms.material.repository.FlashcardRepository;
import com.lms.material.repository.FlashcardReviewRepository;
import lombok.RequiredArgsConstructor;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.entity.FlashcardDeck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final UserRepository userRepository;

    /**
     * Update flashcard content (frontText / backText). Only owner of personal material can edit.
     */
    @Transactional
    public void updateFlashcard(String userEmail, Long flashcardId, FlashcardDto.UpdateReq req) {
        Flashcard flashcard = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", flashcardId));

        // Verify ownership: the material generation must belong to this user
        var matGen = flashcard.getFlashcardDeck().getMaterialGeneration();
        if (!matGen.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Bạn không có quyền sửa flashcard này.");
        }

        if (req.frontText() != null && !req.frontText().isBlank()) {
            flashcard.setFrontText(req.frontText().trim());
        }
        if (req.backText() != null && !req.backText().isBlank()) {
            flashcard.setBackText(req.backText().trim());
        }
        flashcardRepository.save(flashcard);
    }

    /**
     * Delete flashcard. Only owner of personal material can delete.
     */
    @Transactional
    public void deleteFlashcard(String userEmail, Long flashcardId) {
        Flashcard flashcard = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", flashcardId));

        var matGen = flashcard.getFlashcardDeck().getMaterialGeneration();
        if (!matGen.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Bạn không có quyền xóa flashcard này.");
        }

        reviewRepository.deleteByFlashcard_Id(flashcardId);
        flashcardRepository.delete(flashcard);
    }

    /**
     * Add a new flashcard to a deck. Only owner can add.
     */
    @Transactional
    public FlashcardDto.CardWithReview addFlashcard(String userEmail, Long generationId, FlashcardDto.AddReq req) {
        FlashcardDeck deck = flashcardDeckRepository.findByMaterialGeneration_Id(generationId)
                .orElseThrow(() -> new ResourceNotFoundException("FlashcardDeck (by gen id)", generationId));

        if (!deck.getMaterialGeneration().getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Bạn không có quyền thêm flashcard vào bộ này.");
        }

        Flashcard flashcard = new Flashcard();
        flashcard.setFlashcardDeck(deck);
        flashcard.setFrontText(req.frontText().trim());
        flashcard.setBackText(req.backText().trim());
        flashcard = flashcardRepository.save(flashcard);

        return new FlashcardDto.CardWithReview(
                flashcard.getId(), flashcard.getFrontText(), flashcard.getBackText(),
                null, 0, 0, new BigDecimal("2.50"), true
        );
    }

    /**
     * Get all cards in a deck with SRS review state for the current user.
     * Returns cards grouped by: new (no review yet), learning (reviewed but due), review (reviewed and not due).
     */
    @Transactional(readOnly = true)
    public List<FlashcardDto.CardWithReview> getDeckCardsWithReview(String userEmail, Long deckId) {
        User student = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", userEmail));

        List<Flashcard> cards = flashcardRepository.findByFlashcardDeck_Id(deckId);
        List<FlashcardReview> reviews = reviewRepository.findByUser_IdAndFlashcard_FlashcardDeck_Id(student.getId(), deckId);

        // Map flashcardId -> review for O(1) lookup
        Map<Long, FlashcardReview> reviewMap = reviews.stream()
                .collect(Collectors.toMap(r -> r.getFlashcard().getId(), Function.identity()));

        LocalDateTime today = LocalDateTime.now();

        return cards.stream().map(card -> {
            FlashcardReview review = reviewMap.get(card.getId());
            if (review == null) {
                // New card — never reviewed
                return new FlashcardDto.CardWithReview(
                        card.getId(), card.getFrontText(), card.getBackText(),
                        null, 0, 0, new BigDecimal("2.50"), true
                );
            }
            boolean isDue = review.getNextReviewAt() != null && !review.getNextReviewAt().isAfter(today);
            return new FlashcardDto.CardWithReview(
                    card.getId(), card.getFrontText(), card.getBackText(),
                    review.getNextReviewAt(), review.getIntervalDays(),
                    review.getRepetitions(), review.getEasiness(), isDue
            );
        }).toList();
    }

    @Transactional
    public FlashcardDto.ReviewRes reviewCard(String studentEmail, Long flashcardId, FlashcardDto.ReviewReq req) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        
        Flashcard flashcard = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", flashcardId));

        FlashcardReview review = reviewRepository.findByUser_IdAndFlashcard_Id(student.getId(), flashcardId)
                .orElseGet(() -> {
                    FlashcardReview newReview = new FlashcardReview();
                    newReview.setUser(student);
                    newReview.setFlashcard(flashcard);
                    newReview.setEasiness(new BigDecimal("2.50"));
                    newReview.setIntervalDays(0);
                    newReview.setRepetitions(0);
                    newReview.setNextReviewAt(LocalDateTime.now());
                    return newReview;
                });

        int q = req.quality();
        if (q < 0 || q > 5) {
            throw new IllegalArgumentException("Quality must be between 0 and 5");
        }

        int repetitions = review.getRepetitions();
        BigDecimal easiness = review.getEasiness();
        int intervalDays = review.getIntervalDays();
        int intervalMinutes = 0;

        if (q >= 3) {
            if (repetitions == 0) {
                intervalMinutes = q == 5 ? 3 * 24 * 60 : 10;
            } else if (repetitions == 1) {
                intervalMinutes = 6 * 24 * 60;
            } else {
                int days = Math.max(1, (int) Math.round(Math.max(1, intervalDays) * easiness.doubleValue()));
                intervalMinutes = days * 24 * 60;
            }
            repetitions++;
        } else {
            repetitions = 0;
            intervalMinutes = q == 2 ? 6 : 1;
        }

        // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        double newEasiness = easiness.doubleValue() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        if (newEasiness < 1.3) newEasiness = 1.3;
        
        review.setEasiness(BigDecimal.valueOf(newEasiness).setScale(2, RoundingMode.HALF_UP));
        review.setRepetitions(repetitions);
        review.setIntervalDays(Math.max(0, intervalMinutes / (24 * 60)));
        review.setNextReviewAt(LocalDateTime.now().plusMinutes(intervalMinutes));

        review = reviewRepository.save(review);

        return new FlashcardDto.ReviewRes(
                flashcardId,
                review.getNextReviewAt(),
                review.getIntervalDays(),
                review.getRepetitions(),
                review.getEasiness()
        );
    }
}
