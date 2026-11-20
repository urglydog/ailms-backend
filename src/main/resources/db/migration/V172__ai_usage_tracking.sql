-- Theo dõi chi phí AI đầy đủ (21/11/2026): trước đây chỉ Flashcard/Mindmap/Quiz được ghi, còn lồng tiếng, AI Tutor, tìm kiếm AI, giám sát thi, AI hỏi đáp giảng viên và nhúng dữ liệu thì không,
-- nên số liệu hiển thị thấp hơn nhiều so với hóa đơn thật. Chỉ THÊM cột/khóa cấu hình, không đụng dữ liệu cũ.
-- user_id cho phép NULL: nhiều lệnh gọi AI không gắn với một người dùng cụ thể (nhúng dữ liệu khóa học, tìm kiếm của khách).
ALTER TABLE ai_usage_logs MODIFY user_id BIGINT NULL;
ALTER TABLE ai_usage_logs ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'GEMINI';
ALTER TABLE ai_usage_logs ADD COLUMN model VARCHAR(80) NULL;
ALTER TABLE ai_usage_logs ADD COLUMN thinking_tokens INT NOT NULL DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN audio_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN course_id BIGINT NULL;
ALTER TABLE ai_usage_logs ADD COLUMN lesson_id BIGINT NULL;
-- estimated = 1 khi số token là ước lượng (vd nhúng dữ liệu không trả về số token) chứ không phải số liệu do nhà cung cấp báo.
ALTER TABLE ai_usage_logs ADD COLUMN estimated TINYINT(1) NOT NULL DEFAULT 0;

-- Đơn giá ước tính (USD) dùng để quy đổi token thành chi phí; chỉnh theo bảng giá hiện hành của nhà cung cấp để số liệu sát hóa đơn.
INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('AI_PRICE_INPUT_USD_PER_M', '0.30', 'Đơn giá ước tính của token đầu vào Gemini (USD cho 1 triệu token)'),
  ('AI_PRICE_OUTPUT_USD_PER_M', '2.50', 'Đơn giá ước tính của token đầu ra và token suy nghĩ Gemini (USD cho 1 triệu token)'),
  ('AI_PRICE_EMBED_USD_PER_M', '0.15', 'Đơn giá ước tính của nhúng dữ liệu (USD cho 1 triệu token)'),
  ('AI_PRICE_AUDIO_USD_PER_HOUR', '0.04', 'Đơn giá ước tính của nhận dạng giọng nói Groq (USD cho 1 giờ âm thanh)'),
  ('USD_VND_RATE', '25500', 'Tỷ giá USD sang VND dùng để hiển thị chi phí AI'),
  ('AI_COST_TRACKING_SINCE', '2026-11-21', 'Ngày bắt đầu ghi nhận chi phí AI đầy đủ mọi tính năng (trước ngày này chỉ có Flashcard, Mindmap, Quiz)')
ON DUPLICATE KEY UPDATE config_key = config_key;
