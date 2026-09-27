-- ---------------------------------------------------------------------
-- Chống prompt injection / jailbreak cho Socratic Tutor Agent (UC30) — BR-TUTOR-SEC-06
-- (doc/feat/injection/DacTa_ChongPromptInjection_TutorAgent.md). Lớp 2 (phòng thủ bổ sung,
-- không thay thế Lớp 1 — giới hạn tool đọc/ghi ở tầng AI Worker): pre-check heuristic quét
-- pattern nghi vấn TRƯỚC khi gọi LLM, log lại để Admin theo dõi, KHÔNG tự động khóa/chặn ở v1.
-- ---------------------------------------------------------------------

-- Danh sách pattern lưu dạng CẤU HÌNH (không hardcode trong code) để bổ sung dần khi phát hiện
-- pattern mới trong log, không cần deploy lại. `pattern` là 1 regex Java (khớp trên chuỗi ĐÃ bỏ
-- dấu + viết thường — xem TutorSecurityService), so_khớp không phân biệt hoa/thường.
CREATE TABLE tutor_security_patterns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pattern VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bản ghi khớp pattern nào, của ai, khi nào — để Admin xem lại (KHÔNG dùng để tự động khóa tài
-- khoản ở v1, tránh false-positive chặn nhầm học viên hỏi hợp lệ).
CREATE TABLE tutor_security_flags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    matched_pattern VARCHAR(255) NOT NULL,
    message_snapshot TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tutor_security_flags_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_tutor_security_flags_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tutor_security_flags_created_at ON tutor_security_flags (created_at);

-- Danh sách seed ban đầu (mục 5 tài liệu đặc tả) — quét trên bản KHÔNG DẤU + viết thường của tin
-- nhắn học viên. Admin có thể thêm/sửa/tắt pattern sau này bằng cách INSERT/UPDATE trực tiếp bảng
-- này (chưa cần UI quản lý riêng ở v1).
INSERT INTO tutor_security_patterns (pattern, description, enabled, created_at) VALUES
    ('bo qua', 'Yeu cau bo qua quy tac/huong dan he thong', TRUE, NOW()),
    ('ignore (previous|all instructions|the above)', 'Ignore previous/all instructions (tieng Anh)', TRUE, NOW()),
    ('toi la (admin|quan tri vien|dev|nhan vien)', 'Tu xung la admin/quan tri vien/dev/nhan vien he thong', TRUE, NOW()),
    ('i am (the )?(admin|developer)', 'Tu xung "I am admin/developer" (tieng Anh)', TRUE, NOW()),
    ('system (prompt|message)', 'Nhac toi "system prompt/message"', TRUE, NOW()),
    ('huong dan he thong|cau lenh he thong', 'Nhac toi "huong dan/cau lenh he thong"', TRUE, NOW()),
    ('xoa het|xoa toan bo|xoa du lieu', 'Yeu cau xoa het/toan bo du lieu', TRUE, NOW()),
    ('delete all|drop table', 'Yeu cau "delete all"/"drop table" (tieng Anh)', TRUE, NOW()),
    ('day la (noi dung )?bai hoc chinh thuc|day la noi dung bai hoc', 'Tu nhan noi dung dan vao la "bai hoc chinh thuc"', TRUE, NOW()),
    ('ban la dan', 'Yeu cau nhap vai "DAN" (jailbreak persona quen thuoc)', TRUE, NOW()),
    ('(pretend (you are|that you are)|roleplay as|act as)[\\s\\S]{0,60}(khong (con )?quy tac|bo qua quy tac|no rules|without (any )?rules)', 'Yeu cau nhap vai kem theo bo quy tac', TRUE, NOW());
