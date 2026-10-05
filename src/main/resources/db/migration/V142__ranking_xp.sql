-- Ranking cộng đồng theo XP (UpComming_Plan.md, Epic "Ranking cộng đồng") — quy đổi đơn giản
-- theo số lần hành động (hoàn thành bài học/pass quiz chính thức/hoàn thành khóa/giữ streak
-- mỗi ngày), cộng dồn trực tiếp vào 1 cột trên `users` để query leaderboard (ORDER BY total_xp
-- DESC) không cần join/tổng hợp gì thêm — phù hợp quy mô hiện tại, không cần bảng tổng hợp
-- riêng hay cron.
ALTER TABLE users
    ADD COLUMN total_xp BIGINT NOT NULL DEFAULT 0;

-- Leaderboard luôn ORDER BY total_xp DESC — index để tránh full table scan khi số học viên lớn.
CREATE INDEX idx_users_total_xp ON users (total_xp DESC);
