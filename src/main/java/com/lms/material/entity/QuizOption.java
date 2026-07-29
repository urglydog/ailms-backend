package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot phuong an tra loi. Moi cau hoi luon co dung 4 phuong an (BR-QUIZ-01).
 *
 * <p>Dap an dung/sai chi duoc tra ve cho client <b>sau khi nop bai</b>, khong tra
 * trong luc dang lam (BR-QUIZ-02).
 */
@Entity
@Table(name = "quiz_options")
@Getter
@Setter
public class QuizOption extends BaseEntity {

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    private QuizQuestion quizQuestion;
}
