package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Trang thai on tap cua MOT hoc vien tren MOT the, theo thuat toan SM-2 (BR-CARD-01).
 *
 * <p>Tham so: {@code easiness} (EF) khoi tao 2.5 va khong duoi 1.3, {@code intervalDays},
 * {@code repetitions} (so lan lap lien tiep dung). Khoang lap: lan 1 = 1 ngay,
 * lan 2 = 6 ngay, tu lan 3 tinh theo EF. Tra loi sai thi reset ve 1 ngay.
 *
 * <p><b>TODO(doc):</b> anh xa diem chat luong q (Kho/Trung binh/De) va cong thuc
 * I(n), cap nhat EF bi mat khi convert KLTN tu DOCX sang Markdown. Phai doi chieu
 * ban Word truoc khi hien thuc {@code applySM2} - KHONG tu doan so.
 */
@Entity
@Table(name = "flashcard_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_user_card", columnNames = {"user_id", "flashcard_id"}))
@Getter
@Setter
public class FlashcardReview extends BaseEntity {

    /** He so EF cua SM-2: khoi tao 2.5, san 1.3 (BR-CARD-01). */
    @Column(name = "easiness", nullable = false, precision = 4, scale = 2)
    private BigDecimal easiness = new BigDecimal("2.50");

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 0;

    /** So lan lap lien tiep dung (n trong SM-2). */
    @Column(name = "repetitions", nullable = false)
    private Integer repetitions = 0;

    @Column(name = "next_review_at", nullable = false)
    private LocalDate nextReviewAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;
}
