-- =====================================================================
-- Seed dữ liệu tham chiếu cho môi trường DEV
--
-- CHỈ chạy ở profile `dev` — application-dev.yml thêm classpath:db/dev vào
-- flyway.locations. Profile khác không thấy thư mục này.
--
-- Đánh số từ V100 để không bao giờ chen vào dãy version của migration thật
-- (V1, V2, ...) khi các giai đoạn sau thêm bảng.
--
-- Tài khoản người dùng KHÔNG seed ở đây: mật khẩu phải được băm bằng
-- PasswordEncoder thật (Bcrypt cost 12) nên nằm trong DevDataSeeder.java.
-- =====================================================================

-- ── Danh mục môn học (BR-COURSE-05: chỉ Admin quản lý, cấu trúc phẳng) ──
INSERT INTO categories (name, slug, created_at) VALUES
    ('Lập trình Web',        'lap-trinh-web',        NOW()),
    ('Khoa học dữ liệu',     'khoa-hoc-du-lieu',     NOW()),
    ('Trí tuệ nhân tạo',     'tri-tue-nhan-tao',     NOW()),
    ('Thiết kế UI/UX',       'thiet-ke-ui-ux',       NOW()),
    ('Ngoại ngữ chuyên ngành','ngoai-ngu-chuyen-nganh', NOW());

-- ── Bảng ánh xạ giọng đọc (BR-DUB-07) ──────────────────────────────
-- Đây là NGUỒN DUY NHẤT quyết định ngôn ngữ lồng tiếng khả dụng.
-- Học viên chỉ chọn được bản ghi is_active = TRUE.
-- Tên giọng theo danh sách của Edge-TTS.
INSERT INTO voice_mappings (language, voice_name, gender, is_default, is_active, created_at) VALUES
    -- Tiếng Việt
    ('vi-VN', 'vi-VN-HoaiMyNeural',   'FEMALE', TRUE,  TRUE, NOW()),
    ('vi-VN', 'vi-VN-NamMinhNeural',  'MALE',   FALSE, TRUE, NOW()),
    -- Tiếng Anh (Mỹ)
    ('en-US', 'en-US-AriaNeural',     'FEMALE', TRUE,  TRUE, NOW()),
    ('en-US', 'en-US-GuyNeural',      'MALE',   FALSE, TRUE, NOW()),
    -- Tiếng Nhật
    ('ja-JP', 'ja-JP-NanamiNeural',   'FEMALE', TRUE,  TRUE, NOW()),
    ('ja-JP', 'ja-JP-KeitaNeural',    'MALE',   FALSE, TRUE, NOW()),
    -- Tiếng Hàn
    ('ko-KR', 'ko-KR-SunHiNeural',    'FEMALE', TRUE,  TRUE, NOW()),
    ('ko-KR', 'ko-KR-InJoonNeural',   'MALE',   FALSE, TRUE, NOW()),
    -- Tiếng Trung (giản thể)
    ('zh-CN', 'zh-CN-XiaoxiaoNeural', 'FEMALE', TRUE,  TRUE, NOW()),
    ('zh-CN', 'zh-CN-YunxiNeural',    'MALE',   FALSE, TRUE, NOW());
