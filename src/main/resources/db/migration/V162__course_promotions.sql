-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn E) — chương trình quảng bá của nền tảng (tự nguyện, admin duyệt).
-- CHỈ THÊM bảng mới.
--
-- Giảng viên đăng ký quảng bá cho khóa của mình (REQUESTED); admin duyệt (APPROVED) kèm khung thời gian + độ ưu tiên, hoặc từ
-- chối. "Đang chạy" suy ra từ status = APPROVED và start_at <= now < end_at (không cần cron). Vị trí hiển thị: HOME_FEATURED
-- (kệ "Được đề xuất" ở trang chủ), CATEGORY_FEATURED (đầu danh mục). Boost thứ hạng tìm kiếm chưa làm (xem docs/PAYMENT_PAYOUT_EPIC.md E).
CREATE TABLE course_promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    placement VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    request_note VARCHAR(500) NULL,
    start_at DATETIME NULL,
    end_at DATETIME NULL,
    priority INT NOT NULL DEFAULT 0,
    admin_note VARCHAR(500) NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_cp_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_cp_requester FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_cp_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id),
    INDEX idx_cp_active (placement, status, start_at, end_at),
    INDEX idx_cp_course (course_id, placement, status),
    INDEX idx_cp_requester (requested_by_user_id)
);
