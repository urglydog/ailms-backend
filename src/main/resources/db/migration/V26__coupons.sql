-- ---------------------------------------------------------------------
-- Hệ thống mã giảm giá (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57). Xem
-- doc/01_ThietKeLai_GiangVien_ThanhToan_Coupon.md mục 3.3.
-- ---------------------------------------------------------------------
CREATE TABLE coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NULL,
    auto_apply BOOLEAN NOT NULL DEFAULT FALSE,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    max_usage_count INT NULL,
    max_usage_per_user INT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    -- MySQL coi nhiều NULL là KHÁC NHAU trong ràng buộc UNIQUE nên nhiều coupon autoApply
    -- (code=NULL) vẫn thêm được bình thường, chỉ chặn trùng mã khi code có giá trị thật.
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT fk_coupons_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_coupons_active_window ON coupons (is_active, start_at, end_at);

-- Chỉ có dữ liệu khi scope_type = SPECIFIC_COURSES/SINGLE_COURSE.
CREATE TABLE coupon_courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupon_courses_coupon_course UNIQUE (coupon_id, course_id),
    CONSTRAINT fk_coupon_courses_coupon_id FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_courses_course_id FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_coupon_courses_course ON coupon_courses (course_id);
