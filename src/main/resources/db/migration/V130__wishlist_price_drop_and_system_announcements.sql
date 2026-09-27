-- ---------------------------------------------------------------------
-- 1) Wishlist báo giảm giá (26/09/2026, tính năng mới) — cần biết giá tại thời điểm học viên
-- thêm vào wishlist để so sánh khi giảng viên đổi giá sau này (xử lý nền qua Redis, xem
-- WishlistPriceDropService). Backfill NGAY bằng giá hiện tại của khóa học — coi như mọi wishlist
-- cũ được "thêm vào" ở đúng mức giá hiện hành, hợp lý hơn để NULL (sẽ không bao giờ báo được lần
-- giảm giá đầu tiên sau khi thêm cột này).
-- ---------------------------------------------------------------------
ALTER TABLE wishlist_items ADD COLUMN price_at_add DECIMAL(12,2) NULL;
UPDATE wishlist_items wi
    JOIN courses c ON c.id = wi.course_id
    SET wi.price_at_add = c.price
    WHERE wi.price_at_add IS NULL;

-- ---------------------------------------------------------------------
-- 2) Thông báo hệ thống từ Admin — gửi tới TOÀN HỆ THỐNG / chỉ Giảng viên / chỉ Học viên / 1 cá
-- nhân, mức độ HIGH hiển thị thành banner cố định đầu trang (vd "Bảo trì hệ thống"), xử lý phát
-- tán (fan-out) qua job nền Redis thay vì vòng lặp đồng bộ trong request — xem
-- SystemAnnouncementService.
-- ---------------------------------------------------------------------
CREATE TABLE system_announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    audience VARCHAR(20) NOT NULL,
    -- Chỉ có giá trị khi audience = SPECIFIC_USER.
    target_user_id BIGINT NULL,
    -- NULL = hiển thị banner vô thời hạn (chỉ áp dụng khi severity = HIGH) cho tới khi có
    -- thông báo HIGH mới hơn thay thế — không có UI thu hồi ở v1 (giống cách CHƯA làm UI revoke
    -- chứng chỉ, xem doc/DacTa_ChucNangChungChi.md BR-CERT-07).
    expires_at DATETIME NULL,
    dispatch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_recipients INT NULL,
    sent_count INT NOT NULL DEFAULT 0,
    created_by_admin_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_system_announcements_target_user FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_system_announcements_created_by FOREIGN KEY (created_by_admin_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_system_announcements_banner ON system_announcements (severity, expires_at, created_at);
