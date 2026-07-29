package com.lms.dubbing.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.catalog.entity.Lesson;
import com.lms.common.enums.TrackStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * File audio long tieng cua mot bai hoc theo mot ngon ngu (UC16, UC17).
 *
 * <p><b>BR-DUB-04 - tai su dung:</b> UNIQUE {@code (lesson_id, language)} bao dam
 * moi cap bai hoc + ngon ngu chi sinh <b>mot lan</b>. Hoc vien khac chon cung ngon
 * ngu thi phat lai file co san, <b>tuyet doi khong goi lai pipeline AI</b> - goi lai
 * la bug ton tien.
 *
 * <p>{@code status = PARTIAL} nghia la da co chunk phat duoc nhung chua ghep
 * {@code final.mp3}. Frontend BAT BUOC xu ly duoc trang thai nay: phat playlist theo
 * dai thoi gian tung {@link AudioChunk} (BR-CHUNK-05).
 */
@Entity
@Table(name = "audio_tracks",
        uniqueConstraints = @UniqueConstraint(name = "uk_track_lesson_lang", columnNames = {"lesson_id", "language"}))
@Getter
@Setter
public class AudioTrack extends BaseEntity {

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    /** Vi du: vi-VN-HoaiMyNeural. */
    @Column(name = "voice_name", nullable = false, length = 100)
    private String voiceName;

    /** Chi co gia tri sau khi FFmpeg concat xong toan bo chunk (BR-CHUNK-05). */
    @Column(name = "final_url", length = 1000)
    private String finalUrl;

    @Column(name = "duration_sec", nullable = false)
    private Integer durationSec = 0;

    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TrackStatus status = TrackStatus.PROCESSING;

    /** Dung cho bao cao audio khong co luot phat trong 180 ngay (BR-DUB-08). */
    @Column(name = "play_count", nullable = false)
    private Long playCount = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /** Giong su dung phai nam trong danh sach dang active (BR-DUB-07). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_mapping_id", nullable = false)
    private VoiceMapping voiceMapping;
}
