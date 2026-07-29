package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.catalog.entity.Lesson;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Ban ky am cua mot bai hoc theo mot ngon ngu.
 *
 * <p>{@code isSource = true} la ban goc do STT boc ra (BR-DUB-01); cac ban khac la
 * ban dich qua 3 buoc Gemini (BR-DUB-02).
 */
@Entity
@Table(name = "transcripts",
        uniqueConstraints = @UniqueConstraint(name = "uk_transcript_lesson_lang", columnNames = {"lesson_id", "language"}))
@Getter
@Setter
public class Transcript extends BaseEntity {

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    /** TRUE = ban goc do STT sinh ra (BR-DUB-01). */
    @Column(name = "is_source", nullable = false)
    private Boolean isSource = false;

    @Column(name = "full_text", columnDefinition = "LONGTEXT")
    private String fullText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
