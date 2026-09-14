-- ---------------------------------------------------------------------
-- wishlist_items (entity: WishlistItem, module: wishlist) — TÍNH NĂNG MỞ RỘNG (14/09/2026),
-- KHÔNG nằm trong 49 use case đặc tả gốc của đồ án — học viên lưu lại khóa học CHƯA SỞ HỮU
-- để theo dõi (Udemy-style wishlist). Độc lập với cart_items (V19) — 1 khóa có thể vừa nằm
-- trong giỏ hàng vừa nằm trong wishlist, không loại trừ nhau.
-- ---------------------------------------------------------------------
CREATE TABLE wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wishlist_items_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_wishlist_items_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wishlist_items_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_wishlist_items_user ON wishlist_items (user_id);
