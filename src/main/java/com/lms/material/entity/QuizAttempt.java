package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot lan lam bai Quiz.
 *
 * <p>BR-QUIZ-01: hoc vien lam lai <b>khong gioi han so lan</b> va he thong luu
 * <b>toan bo</b> lich su - vi vay <b>KHONG</b> dat UNIQUE tren (user_id, quiz_id).
 * Diem tinh theo ty le cau dung, thang diem 10.
 *
 * <p>BR-PROGRESS-04: diem hien trong bao cao tien do la MAX(score) tren <b>moi bo
 * Quiz</b> cua khoa hoc do, khong tach theo tung bo hay tung ngon ngu.
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
public class QuizAttempt extends BaseEntity {

    /** Thang diem 10 (BR-QUIZ-01). */
    @Column(name = "score", nullable = false, precision = 4, scale = 2)
    private BigDecimal score;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
