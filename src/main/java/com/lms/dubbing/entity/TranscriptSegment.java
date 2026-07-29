package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot cau thoai kem moc thoi gian o cap do tu (BR-DUB-01).
 *
 * <p>Moc thoi gian o day la du lieu nen tang cho hai viec: tinh {@code T_orig} cua
 * thuat toan Adaptive Speech Rate (BR-DUB-03) va trich dan moc thoi gian nhap duoc
 * cua Socratic Tutor (BR-TUTOR-02).
 */
@Entity
@Table(name = "transcript_segments",
        uniqueConstraints = @UniqueConstraint(name = "uk_segment_transcript_seq", columnNames = {"transcript_id", "seq"}))
@Getter
@Setter
public class TranscriptSegment extends BaseEntity {

    /** Thu tu cau trong bai. */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    /** Do chinh xac toi mili-giay (word-level timestamp). */
    @Column(name = "start_sec", nullable = false, precision = 10, scale = 3)
    private BigDecimal startSec;

    @Column(name = "end_sec", nullable = false, precision = 10, scale = 3)
    private BigDecimal endSec;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    /** He so R da ap dung khi tong hop giong (BR-DUB-03). */
    @Column(name = "speech_rate", precision = 5, scale = 2)
    private BigDecimal speechRate;

    /** TRUE neu cau nay da qua buoc LLM Re-summarization vi R > 1.3 (BR-DUB-03). */
    @Column(name = "was_summarized", nullable = false)
    private Boolean wasSummarized = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    /** T_orig trong cong thuc Adaptive Speech Rate (BR-DUB-03). */
    public BigDecimal originalDuration() {
        return endSec.subtract(startSec);
    }
}
