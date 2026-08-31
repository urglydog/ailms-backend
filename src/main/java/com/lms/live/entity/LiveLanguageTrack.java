package com.lms.live.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.live.enums.LiveTrackStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Track lồng tiếng thời gian thực của 1 {@link LiveSession} (UC52, F11.3).
 *
 * <p>BR-LIVE-05 — đúng 1 track {@code ACTIVE}/ngôn ngữ/phiên tại 1 thời điểm: học viên đầu tiên
 * chọn ngôn ngữ tạo track này và quyết định {@link #voiceName}; học viên sau cùng ngôn ngữ chỉ
 * tăng {@link #activeListenerCount}, không tạo track mới, không đổi giọng. Track tự dừng
 * ({@code status=STOPPED}) khi {@link #activeListenerCount} về 0.
 */
@Entity
@Table(name = "live_language_tracks")
@Getter
@Setter
public class LiveLanguageTrack extends BaseEntity {

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    /** Chốt bởi học viên ĐẦU TIÊN kích hoạt ngôn ngữ này — không đổi được sau đó (BR-LIVE-05). */
    @Column(name = "voice_name", nullable = false, length = 100)
    private String voiceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LiveTrackStatus status = LiveTrackStatus.ACTIVE;

    @Column(name = "active_listener_count", nullable = false)
    private Integer activeListenerCount = 0;

    /** Thủ thuật UNIQUE giống {@code AiJob.activeFlag} (BR-DUB-05) — xem javadoc migration V13. */
    @Column(name = "active_flag")
    private Integer activeFlag = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "live_session_id", nullable = false)
    private LiveSession liveSession;

    public void releaseActiveFlag() {
        this.activeFlag = null;
    }
}
