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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final UserRepository userRepository;

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
                    newReview.setNextReviewAt(LocalDate.now());
                    return newReview;
                });

        int q = req.quality();
        if (q < 0 || q > 5) {
            throw new IllegalArgumentException("Quality must be between 0 and 5");
        }

        int repetitions = review.getRepetitions();
        BigDecimal easiness = review.getEasiness();
        int intervalDays = review.getIntervalDays();

        if (q >= 3) {
            if (repetitions == 0) {
                intervalDays = 1;
            } else if (repetitions == 1) {
                intervalDays = 6;
            } else {
                intervalDays = Math.max(1, (int) Math.round(intervalDays * easiness.doubleValue()));
            }
            repetitions++;
        } else {
            repetitions = 0;
            intervalDays = 1;
        }

        // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        double newEasiness = easiness.doubleValue() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        if (newEasiness < 1.3) newEasiness = 1.3;
        
        review.setEasiness(BigDecimal.valueOf(newEasiness).setScale(2, RoundingMode.HALF_UP));
        review.setRepetitions(repetitions);
        review.setIntervalDays(intervalDays);
        review.setNextReviewAt(LocalDate.now().plusDays(intervalDays));

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
