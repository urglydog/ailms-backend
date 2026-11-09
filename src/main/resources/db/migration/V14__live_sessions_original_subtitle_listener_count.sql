-- Giai đoạn 11 (F11.5 mở rộng) — đếm số người đang xem "Phụ đề gốc", độc lập hoàn toàn với
-- lồng tiếng (LiveLanguageTrack). Chỉ 1 luồng nhận diện giọng nói gốc/phiên nên không cần bảng
-- riêng như live_language_tracks, chỉ 1 counter ngay trên live_sessions là đủ.
ALTER TABLE live_sessions
    ADD COLUMN original_subtitle_listener_count INT NOT NULL DEFAULT 0 AFTER instructor_disconnected_at;
