package com.lms.material.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.lms.material.repository.projection.FlashcardDueCount;
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
    void deleteByFlashcard_FlashcardDeck_Id(Long deckId);
    void deleteByFlashcard_Id(Long flashcardId);
    int countByFlashcard_FlashcardDeck_Id(Long deckId);
    /**
     * Số thẻ đến hạn ôn (nextReviewAt ≤ now) theo từng học viên — đếm và gom nhóm NGAY TRONG DB.
     * Trước đây job nạp TOÀN BỘ bảng flashcard_reviews rồi lọc bằng Java (hiệu năng đợt 3).
     */
    @Query("SELECT new com.lms.material.repository.projection.FlashcardDueCount(r.user.email, COUNT(r)) "
            + "FROM FlashcardReview r WHERE r.nextReviewAt <= :now GROUP BY r.user.email")
    java.util.List<FlashcardDueCount> countDueByUser(@Param("now") java.time.LocalDateTime now);

}
