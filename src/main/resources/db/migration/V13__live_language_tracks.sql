-- Giai đoạn 11 (F11.3) — UC52: track lồng tiếng thời gian thực của 1 phiên live, theo BR-LIVE-05.
-- active_flag dùng đúng thủ thuật giống ai_jobs (BR-DUB-05): MySQL không có partial unique index,
-- nên UNIQUE thật (live_session_id, target_language, active_flag) — 1 khi ACTIVE, NULL khi STOPPED
-- (MySQL cho nhiều dòng NULL trong UNIQUE key, nên track cũ đã dừng không chặn track mới cùng cặp).
CREATE TABLE live_language_tracks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    live_session_id BIGINT NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    voice_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_listener_count INT NOT NULL DEFAULT 0,
    active_flag INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_live_track_session_lang_active UNIQUE (live_session_id, target_language, active_flag),
    CONSTRAINT fk_live_language_tracks_session FOREIGN KEY (live_session_id) REFERENCES live_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_live_language_tracks_session ON live_language_tracks (live_session_id);
