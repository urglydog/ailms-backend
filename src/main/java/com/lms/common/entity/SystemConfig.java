package com.lms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình hệ thống dạng key-value, sửa được từ trang Admin (23/10/2026) — thay cho mảng
 * QUOTAS hardcode trước đó trong {@code app/admin/settings/page.tsx} (FE), hoàn toàn không
 * sửa được dù trang mang tên "Cấu hình hệ thống".
 *
 * <p>Tối giản có chủ đích: v1 chỉ lưu giá trị, KHÔNG nối các service enforce quota (BR-DUB-06,
 * BR-MAT-08, BR-TUTOR-04, BR-DISCOVERY-01) đọc từ bảng này — việc đó để task riêng sau.
 */
@Entity
@Table(name = "system_configs")
@Getter
@Setter
public class SystemConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 50)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(name = "description", length = 255)
    private String description;
}
