package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.catalog.entity.Lesson;
import com.lms.auth.entity.User;
import com.lms.common.enums.JobStatus;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tac vu AI bat dong bo, chu yeu la long tieng (UC18, UC19, UC45).
 *
 * <p><b>BR-DUB-05 - chong trung job, can CA HAI lop:</b>
 * <ol>
 *   <li>UNIQUE {@code (lesson_id, target_language, active_flag)} o CSDL. MySQL
 *       <b>khong co partial unique index</b>, nen dung cot phu {@link #activeFlag}:
 *       gan 1 khi job dang PENDING/PROCESSING, gan <b>NULL</b> khi job ket thuc.
 *       MySQL cho phep nhieu dong NULL trong UNIQUE key nen job da xong khong chan
 *       job moi.</li>
 *   <li>Redis SETNX lock:dub:&#123;lessonId&#125;:&#123;lang&#125; TTL 30 phut de chan
 *       race giua nhieu instance backend.</li>
 * </ol>
 * Hoc vien thu n bam khi da co job dang chay thi <b>khong tao job moi</b>, chi dang ky
 * nhan WebSocket - va lan bam do <b>khong tinh</b> vao han ngach BR-DUB-06.
 */
@Entity
@Table(name = "ai_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_aijob_lesson_lang_active", columnNames = {"lesson_id", "target_language", "active_flag"}))
@Getter
@Setter
public class AiJob extends BaseEntity {

    @Column(name = "job_type", nullable = false, length = 30)
    private String jobType = "DUBBING";

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    /**
     * Cot phu cho UNIQUE constraint: 1 khi job dang chay, NULL khi da ket thuc.
     * Xem javadoc cua class de hieu vi sao can cot nay (BR-DUB-05).
     */
    @Column(name = "active_flag")
    private Integer activeFlag = 1;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks = 0;

    @Column(name = "done_chunks", nullable = false)
    private Integer doneChunks = 0;

    @Column(name = "celery_task_id", length = 100)
    private String celeryTaskId;

    /** Toi da 3 lan voi exponential backoff (BR-CHUNK-04). */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /**
     * Nguoi da bam kich hoat (BR-NOTIFY-01: bao DUBBING_COMPLETED/FAILED dung nguoi).
     * Nullable vi job cu truoc F5.3 khong co du lieu nay.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    /** Tien do hien thi cho UC20. */
    public int progressPercent() {
        if (totalChunks == null || totalChunks == 0) {
            return 0;
        }
        return (int) Math.round(doneChunks * 100.0 / totalChunks);
    }

    /** BR-CHUNK-04: chi retry khi chua qua 3 lan. */
    public boolean canRetry() {
        return retryCount != null && retryCount < 3;
    }

    /** Goi khi job ket thuc de giai phong UNIQUE constraint (BR-DUB-05). */
    public void releaseActiveFlag() {
        this.activeFlag = null;
    }
}
