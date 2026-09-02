package com.lms.material.repository;

import com.lms.material.entity.FlashcardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link FlashcardReview}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {
    java.util.Optional<FlashcardReview> findByUser_IdAndFlashcard_Id(Long userId, Long flashcardId);
    java.util.List<FlashcardReview> findByUser_IdAndFlashcard_FlashcardDeck_Id(Long userId, Long deckId);
}
