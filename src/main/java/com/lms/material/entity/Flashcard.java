package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot the ghi nho: mat truoc va mat sau.
 *
 * <p>Noi dung the la chung cho bo the, nhung <b>tien do on tap la rieng tung hoc
 * vien</b> - xem {@link FlashcardReview}.
 */
@Entity
@Table(name = "flashcards")
@Getter
@Setter
public class Flashcard extends BaseEntity {

    /** Mat truoc: cau hoi hoac thuat ngu. */
    @Column(name = "front_text", columnDefinition = "TEXT", nullable = false)
    private String frontText;

    /** Mat sau: giai thich. */
    @Column(name = "back_text", columnDefinition = "TEXT", nullable = false)
    private String backText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_deck_id", nullable = false)
    private FlashcardDeck flashcardDeck;
}
