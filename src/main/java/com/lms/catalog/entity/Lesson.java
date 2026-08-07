package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bài học chứa video (UC33, UC34).
 *
 * <p>{@code sourceLanguage} do WhisperX/Groq tự điền sau lần bóc băng đầu tiên và
 * được dùng để chặn lồng tiếng trùng ngôn ngữ gốc (BR-DUB-09).
 *
 * <p>{@code status}: DRAFT -> READY (đã có video hợp lệ) -> UNAVAILABLE (nguồn video
 * bị gỡ — BR-DUB-11, file audio đã sinh vẫn được giữ).
 */
@Entity
@Table(name = "lessons")
@Getter
@Setter
public class Lesson extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * UPLOAD hoac YOUTUBE. NULL cho tới khi nạp video ở Giai đoạn 4 — F2.1 chỉ tạo
     * bài học với metadata (title), chưa xử lý video (UC33 tách khỏi UC34).
     */
    @Column(name = "video_source", length = 20)
    private String videoSource;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    /** Chỉ có giá trị khi videoSource = YOUTUBE. */
    @Column(name = "youtube_id", length = 50)
    private String youtubeId;

    /** BR-CHUNK-01: thời lượng cho phép 1-180 phút. */
    @Column(name = "duration_sec", nullable = false)
    private Integer durationSec = 0;

    /** Tự điền sau lần bóc băng đầu tiên (BR-DUB-09). */
    @Column(name = "source_language", length = 10)
    private String sourceLanguage;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Guest xem được video nhưng không dùng AI Tutor/Quiz/Flashcards. Không giới hạn số lượng. */
    @Column(name = "is_preview", nullable = false)
    private Boolean isPreview = false;

    /** DRAFT / READY / UNAVAILABLE (BR-DUB-11). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;
}
