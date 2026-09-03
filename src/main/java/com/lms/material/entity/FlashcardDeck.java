package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bo the ghi nho (UC28).
 *
 * <p>So the theo {@code quantityLevel}: FEWER 20-30, STANDARD 40-60, MORE 60-80
 * (BR-MAT-05). Pham vi hep hon thi tinh ty le, san toi thieu 10 the.
 */
@Entity
@Table(name = "flashcard_decks")
@Getter
@Setter
public class FlashcardDeck extends BaseEntity {

    @Column(name = "card_count", nullable = false)
    private Integer cardCount = 0;

    /** Hoc lieu chuan do Giang vien danh dau (Cong viec 5). */
    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial = false;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_generation_id", nullable = false, unique = true)
    private MaterialGeneration materialGeneration;
}
