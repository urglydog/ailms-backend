package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot cau hoi trac nghiem.
 *
 * <p><b>CO Y KHONG CO {@code explanation} va {@code timestampSec}</b> - da bo theo
 * BR-QUIZ-01/BR-QUIZ-02. Hoc vien muon hieu sau thi chu dong hoi Socratic Tutor;
 * lam vay tiet kiem chi phi sinh giai thich va dung tinh than "hoc chu dong, goi mo"
 * cua de tai. <b>Dung them lai hai cot nay.</b>
 *
 * <p>Luon co <b>dung 4</b> {@link QuizOption} (BR-QUIZ-01).
 */
@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
public class QuizQuestion extends BaseEntity {

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;
}
