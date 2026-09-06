-- ---------------------------------------------------------------------
-- cart_items (entity: CartItem, module: payment) — TÍNH NĂNG MỞ RỘNG (06/09/2026),
-- KHÔNG nằm trong 49 use case đặc tả gốc của đồ án — học viên thêm khóa học TRẢ PHÍ
-- vào giỏ hàng trước khi thanh toán, thay vì chỉ mua ngay từng khóa một (UC13/UC14 cũ
-- vẫn giữ nguyên, dùng song song với luồng giỏ hàng này).
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_cart_items_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_cart_items_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cart_items_user ON cart_items (user_id);

-- Thanh toán GỘP nhiều khóa học trong 1 lần bấm "Proceed to Checkout" từ giỏ hàng (học
-- viên tự chọn qua checkbox, không bắt buộc thanh toán hết giỏ) — các Payment tạo cùng
-- 1 lần checkout dùng CHUNG giá trị này để cổng thanh toán xác nhận CẢ NHÓM cùng lúc qua
-- 1 giao dịch/webhook duy nhất, trong khi mỗi Payment vẫn giữ `txn_ref` RIÊNG (UNIQUE
-- từng dòng, không đổi ràng buộc cũ — mỗi khóa vẫn tách bạch hoa hồng giảng viên đúng
-- BR-PAY-05). NULL ở luồng mua 1 khóa trực tiếp (UC13/UC14 cũ) — không đổi hành vi cũ.
ALTER TABLE payments ADD COLUMN order_group_ref VARCHAR(50) NULL AFTER txn_ref;
CREATE INDEX idx_payments_order_group_ref ON payments (order_group_ref);
