-- ---------------------------------------------------------------------
-- Thiết kế lại luồng đăng ký Giảng viên (15/09/2026) — thay UC41 "Admin duyệt yêu cầu nâng
-- cấp Giảng viên" (bảng instructor_requests, PENDING/APPROVED/REJECTED) bằng nâng cấp NGAY
-- LẬP TỨC khi học viên bấm nút (không cần Admin duyệt), kèm 1 bước "xác minh thông tin định
-- danh" (instructor_verifications) bắt buộc hoàn tất 1 LẦN DUY NHẤT/tài khoản trước khi gửi
-- khóa học ĐẦU TIÊN đi duyệt — xem doc/01_ThietKeLai_GiangVien_ThanhToan_Coupon.md.
--
-- Không còn code nào tạo/đọc instructor_requests nữa nên xoá hẳn bảng (dự án quy mô đồ án,
-- không có dữ liệu thật cần giữ lại).
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS instructor_requests;

CREATE TABLE instructor_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    id_number VARCHAR(50) NOT NULL,
    id_photo_url VARCHAR(500) NOT NULL,
    address_text VARCHAR(500) NOT NULL,
    content_ownership_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_instructor_verifications_user UNIQUE (user_id),
    CONSTRAINT fk_instructor_verifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
