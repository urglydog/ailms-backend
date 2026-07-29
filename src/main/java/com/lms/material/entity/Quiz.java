package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bai kiem tra trac nghiem (UC29).
 *
 * <p>So cau theo {@code quantityLevel}: FEWER 10-15, STANDARD 20-30, MORE 30-40
 * (BR-MAT-05). Pham vi hep hon thi tinh ty le, san toi thieu 5 cau.
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
public class Quiz extends BaseEntity {

    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 0;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_generation_id", nullable = false, unique = true)
    private MaterialGeneration materialGeneration;
}
