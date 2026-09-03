package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bai kiem tra trac nghiem (UC29).
 *
 * <p>So cau theo {@code quantityLevel}: FEWER 10-15, STANDARD 20-30, MORE 30-40
 * (BR-MAT-05). Pham vi hep hon thi tinh ty le, san toi thieu 5 cau.
 *
 * <p><b>Cau hinh thi do Giang vien quan ly (Cong viec 5):</b>
 * <ul>
 *   <li>{@code randomPickCount}: so cau boc ngau nhien moi lan thi (NULL = lay het).</li>
 *   <li>{@code allowReview}: cho phep hoc vien xem lai dap an (default TRUE).</li>
 *   <li>{@code startTime}/{@code endTime}: khung gio mo/dong de thi.</li>
 *   <li>{@code durationMinutes}: gioi han thoi gian lam bai (phut).</li>
 *   <li>{@code maxAttempts}: so lan lam bai toi da (NULL = Practice khong gioi han).</li>
 * </ul>
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
public class Quiz extends BaseEntity {

    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 0;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial = false;

    /** So cau boc ngau nhien moi lan thi. NULL = lay het cau hoi. */
    @Column(name = "random_pick_count")
    private Integer randomPickCount;

    /** Cho phep hoc vien xem lai dap an sau khi nop bai. */
    @Column(name = "allow_review", nullable = false)
    private Boolean allowReview = true;

    /** Thoi diem mo de thi. NULL = mo ngay. */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** Thoi diem dong de thi (Deadline). NULL = khong gioi han. */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** Gioi han thoi gian lam bai (phut). NULL = khong gioi han. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** So lan lam bai toi da. NULL = khong gioi han (Practice mode). */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_generation_id", nullable = false, unique = true)
    private MaterialGeneration materialGeneration;
}
