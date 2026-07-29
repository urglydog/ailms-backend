package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot phan doan audio da tong hop, phat duoc ngay (BR-CHUNK-05).
 *
 * <p>Ton tai de phuc vu xu ly luy tien: khi chua co {@code final.mp3}, frontend phat
 * playlist cac chunk nay theo dai thoi gian {@code startSec}-{@code endSec}.
 */
@Entity
@Table(name = "audio_chunks",
        uniqueConstraints = @UniqueConstraint(name = "uk_audiochunk_track_index", columnNames = {"audio_track_id", "chunk_index"}))
@Getter
@Setter
public class AudioChunk extends BaseEntity {

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "start_sec", nullable = false)
    private Integer startSec;

    @Column(name = "end_sec", nullable = false)
    private Integer endSec;

    /** Duong dan chunk_&#123;i&#125;.mp3 tren Cloud Storage. */
    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audio_track_id", nullable = false)
    private AudioTrack audioTrack;
}
