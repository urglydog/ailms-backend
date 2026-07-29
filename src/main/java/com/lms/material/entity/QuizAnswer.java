package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Cau tra loi cua hoc vien cho mot cau hoi trong mot lan lam bai.
 */
@Entity
@Table(name = "quiz_answers")
@Getter
@Setter
public class QuizAnswer extends BaseEntity {

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    /** Phuong an hoc vien da chon. NULL neu bo trong cau nay. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuizOption selectedOption;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id", nullable = false)
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    private QuizQuestion quizQuestion;
}
