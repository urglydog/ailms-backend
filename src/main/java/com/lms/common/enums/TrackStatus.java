package com.lms.common.enums;

/**
 * Trạng thái track audio lồng tiếng (BR-CHUNK-05).
 * PARTIAL = đã có chunk phát được nhưng chưa ghép final.mp3.
 * Frontend BẮT BUỘC xử lý được PARTIAL: phát playlist chunk theo dải thời gian.
 *
 * <p>Dùng bởi: AudioTrack</p>
 */
public enum TrackStatus {
    PROCESSING,
    PARTIAL,
    COMPLETED,
    FAILED
}
