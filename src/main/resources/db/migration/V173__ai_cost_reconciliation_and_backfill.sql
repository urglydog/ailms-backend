-- Đối soát chi phí AI với hóa đơn nhà cung cấp + ước tính chi phí lịch sử từ dữ liệu đã lưu (21/11/2026).
-- Chỉ THÊM bảng/dòng; không xóa hay sửa dữ liệu cũ (chỉ cập nhật 4 khóa đơn giá nếu admin chưa từng đổi khỏi mặc định cũ).

-- Hóa đơn thực tế của nhà cung cấp theo từng khoảng ngày (admin nhập từ trang thanh toán của Google AI Studio / Groq). Không chồng lấn khoảng ngày để khỏi đếm đôi.
CREATE TABLE ai_billing_entries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  amount_vnd BIGINT NOT NULL,
  source VARCHAR(100) NOT NULL,
  note VARCHAR(300) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_ai_billing_period (period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Hai số đọc từ trang Usage của Google AI Studio (dự án KHOALUAN, chụp 21/11/2026): tổng 90 ngày 364K, tháng 11 là 220.739đ → 24/8–31/10 suy ra 143.261đ.
INSERT INTO ai_billing_entries (period_start, period_end, amount_vnd, source, note, created_at) VALUES
  ('2026-08-24', '2026-10-31', 143261, 'Google AI Studio', 'Suy ra từ tổng 364K (24/8–21/11) trừ chi phí tháng 11; số làm tròn theo trang Usage', NOW()),
  ('2026-11-01', '2026-11-21', 220739, 'Google AI Studio', 'Chi phí tháng 11 theo trang Usage (hạn mức chi tiêu tháng)', NOW());

-- Đơn giá dự phòng (cho model chưa có trong bảng giá của hệ thống) theo Gemini 3.6 Flash giá khuyến mãi; chỉ đổi nếu vẫn là mặc định cũ.
UPDATE system_configs SET config_value = '0.75' WHERE config_key = 'AI_PRICE_INPUT_USD_PER_M' AND config_value = '0.30';
UPDATE system_configs SET config_value = '3.75' WHERE config_key = 'AI_PRICE_OUTPUT_USD_PER_M' AND config_value = '2.50';

-- ===== Ước tính lịch sử từ dữ liệu đã lưu (đánh dấu estimated = 1) =====
-- Chỉ tính phần NHÌN THẤY được: số ký tự lời thoại gốc/đã dịch của từng lượt lồng tiếng hoàn tất (≈ 3 ký tự/token, ≈ 1,2 ký tự/token với tiếng Trung/Nhật/Hàn, +800 token chỉ dẫn).
-- Token "suy nghĩ", lệnh gọi thử/thất bại không để lại dấu vết nên KHÔNG có ở đây; phần chênh với hóa đơn thật hiển thị là "chưa quy được" ở trang AI Analytics.
-- Giá: tháng 11/2026 trở đi model gemini-3.6-flash (0.75/3.75), trước đó gemini-3.5-flash (1.50/9.00) — khớp bảng giá trong AiPricing.
INSERT INTO ai_usage_logs (user_id, feature_type, prompt_tokens, completion_tokens, total_tokens, cost_usd, created_at, updated_at, provider, model, thinking_tokens, audio_seconds, course_id, lesson_id, estimated)
SELECT t.requested_by_user_id, 'DUBBING_TRANSLATE', t.p, t.c, t.p + t.c,
       ROUND(t.p * IF(t.fin >= '2026-11-01', 0.75, 1.50) / 1000000 + t.c * IF(t.fin >= '2026-11-01', 3.75, 9.00) / 1000000, 6),
       t.fin, t.fin, 'GEMINI', IF(t.fin >= '2026-11-01', 'gemini-3.6-flash', 'gemini-3.5-flash'), 0, 0, t.course_id, t.lesson_id, 1
FROM (
  SELECT j.requested_by_user_id, j.lesson_id, ch.course_id, COALESCE(j.finished_at, j.created_at) AS fin,
         CEIL(COALESCE(src.chars, 0) / 3) + 800 AS p,
         CEIL(COALESCE(tgt.chars, 0) / IF(j.target_language REGEXP '^(zh|ja|ko)', 1.2, 3)) AS c
  FROM ai_jobs j
  JOIN lessons l ON l.id = j.lesson_id
  JOIN chapters ch ON ch.id = l.chapter_id
  LEFT JOIN (SELECT tr.lesson_id, SUM(CHAR_LENGTH(s.text)) AS chars FROM transcripts tr JOIN transcript_segments s ON s.transcript_id = tr.id WHERE tr.is_source = 1 GROUP BY tr.lesson_id) src ON src.lesson_id = j.lesson_id
  LEFT JOIN (SELECT tr.lesson_id, tr.language, SUM(CHAR_LENGTH(s.text)) AS chars FROM transcripts tr JOIN transcript_segments s ON s.transcript_id = tr.id WHERE tr.is_source = 0 GROUP BY tr.lesson_id, tr.language) tgt
         ON tgt.lesson_id = j.lesson_id AND tgt.language = j.target_language
  WHERE j.status = 'COMPLETED'
) t;

-- Nhận dạng giọng nói (Groq) cho các lượt lồng tiếng hoàn tất mà bài học có bản lời thoại gốc: tính theo thời lượng bài học (0.04 USD/giờ âm thanh).
INSERT INTO ai_usage_logs (user_id, feature_type, prompt_tokens, completion_tokens, total_tokens, cost_usd, created_at, updated_at, provider, model, thinking_tokens, audio_seconds, course_id, lesson_id, estimated)
SELECT j.requested_by_user_id, 'DUBBING_TRANSCRIBE', 0, 0, 0, ROUND(COALESCE(l.duration_sec, 0) * 0.04 / 3600, 6),
       COALESCE(j.finished_at, j.created_at), COALESCE(j.finished_at, j.created_at), 'GROQ', 'whisper-large-v3-turbo', 0, COALESCE(l.duration_sec, 0), ch.course_id, j.lesson_id, 1
FROM ai_jobs j
JOIN lessons l ON l.id = j.lesson_id
JOIN chapters ch ON ch.id = l.chapter_id
WHERE j.status = 'COMPLETED' AND COALESCE(l.duration_sec, 0) > 0
  AND EXISTS (SELECT 1 FROM transcripts tr WHERE tr.lesson_id = j.lesson_id AND tr.is_source = 1);

-- Hỏi đáp với Gia sư AI: mỗi câu trả lời của AI ≈ 1.500 token ngữ cảnh + độ dài câu hỏi + độ dài câu trả lời (dùng token_used nếu có).
INSERT INTO ai_usage_logs (user_id, feature_type, prompt_tokens, completion_tokens, total_tokens, cost_usd, created_at, updated_at, provider, model, thinking_tokens, audio_seconds, course_id, lesson_id, estimated)
SELECT s.user_id, 'TUTOR_CHAT', 1500, GREATEST(COALESCE(m.token_used, 0), CEIL(CHAR_LENGTH(m.content) / 3)),
       1500 + GREATEST(COALESCE(m.token_used, 0), CEIL(CHAR_LENGTH(m.content) / 3)),
       ROUND(1500 * 1.50 / 1000000 + GREATEST(COALESCE(m.token_used, 0), CEIL(CHAR_LENGTH(m.content) / 3)) * 9.00 / 1000000, 6),
       m.created_at, m.created_at, 'GEMINI', 'gemini-3.5-flash', 0, 0, s.course_id, m.context_lesson_id, 1
FROM chat_messages m
JOIN chat_sessions s ON s.id = m.chat_session_id
WHERE m.sender = 'AI' AND m.created_at < '2026-11-01';
