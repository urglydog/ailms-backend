package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.JobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot phan doan cua job long tieng (BR-CHUNK-02).
 *
 * <p>Video dai hon nguong bi chia thanh cac phan doan co dinh <b>10 phut</b>; phan
 * doan cuoi chua phan du. Phan doan dau xong la ban WebSocket cho hoc vien vao hoc
 * ngay (BR-CHUNK-03).
 *
 * <p>Phan doan {@code FAILED} sau 3 lan retry thi <b>giu audio goc rieng doan do</b>,
 * cac doan khac van phat ban long tieng (BR-CHUNK-04).
 */
@Entity
@Table(name = "ai_job_chunks",
        uniqueConstraints = @UniqueConstraint(name = "uk_chunk_job_index", columnNames = {"ai_job_id", "chunk_index"}))
@Getter
@Setter
public class AiJobChunk extends BaseEntity {

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "start_sec", nullable = false)
    private Integer startSec;

    @Column(name = "end_sec", nullable = false)
    private Integer endSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_job_id", nullable = false)
    private AiJob aiJob;
}
