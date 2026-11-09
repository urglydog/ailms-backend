-- Giai đoạn 11 (F11.1) — UC50: vòng đời phiên Live Classroom (SCHEDULED -> LIVE -> ENDED).
-- LiveLanguageTrack/LiveChatMessage thuộc F11.3/F11.4, tạo ở migration riêng khi tới lượt.
--
-- scheduled_at và instructor_disconnected_at KHÔNG có trong bảng entity gốc của
-- doc/DacTa_LiveClassroom.md — bổ sung cho phần hiện thực:
--   * scheduled_at: cần có "giờ dự kiến" để học viên thấy banner "sắp live" (quyết định giữ
--     SCHEDULED đã chốt với người dùng qua AskUserQuestion), bản đặc tả gốc chỉ có startedAt
--     (giờ THỰC TẾ bắt đầu, khác giờ DỰ KIẾN).
--   * instructor_disconnected_at: mốc thời gian giảng viên rớt kết nối lần gần nhất, LiveKit
--     webhook (participant_left/participant_joined) ghi/xoá cột này; LiveSessionCronJob quét
--     mỗi 15s, quá 60s kể từ mốc này mà chưa có participant_joined xoá lại thì tự chuyển ENDED
--     (BR-LIVE-09). Không lưu vĩnh viễn — chỉ có ý nghĩa khi status = LIVE.
CREATE TABLE live_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    instructor_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    room_name VARCHAR(150) NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    scheduled_at DATETIME NULL,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    instructor_disconnected_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_live_sessions_room_name UNIQUE (room_name),
    CONSTRAINT fk_live_sessions_instructor FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT fk_live_sessions_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_live_sessions_course ON live_sessions (course_id);
CREATE INDEX idx_live_sessions_instructor ON live_sessions (instructor_id);
-- BR-LIVE-09 cron job quét theo đúng cặp cột này (WHERE status = 'LIVE' AND instructor_disconnected_at <= ?).
CREATE INDEX idx_live_sessions_status_disconnect ON live_sessions (status, instructor_disconnected_at);
