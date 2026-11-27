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
     * Số thẻ đến hạn ôn (nextReviewAt ≤ now) theo từng học viên VÀ từng khóa học — đếm và gom nhóm NGAY TRONG DB.
     * Trước đây job nạp TOÀN BỘ bảng flashcard_reviews rồi lọc bằng Java (hiệu năng đợt 3).
     */
    @Query("SELECT new com.lms.material.repository.projection.FlashcardDueCount(r.user.id, c.id, c.title, COUNT(r)) "
            + "FROM FlashcardReview r JOIN r.flashcard f JOIN f.flashcardDeck d JOIN d.materialGeneration g JOIN g.course c "
            + "WHERE r.nextReviewAt <= :now AND g.isDeleted = false "
            // Chỉ nhắc học viên CÒN ghi danh khoá đó (ghi danh bị thu hồi khi hoàn tiền thì `@SQLRestriction` của Enrollment tự loại).
            + "AND EXISTS (SELECT 1 FROM Enrollment en WHERE en.user.id = r.user.id AND en.course.id = c.id) "
            + "GROUP BY r.user.id, c.id, c.title")
    java.util.List<FlashcardDueCount> countDueByUser(@Param("now") java.time.LocalDateTime now);

}
