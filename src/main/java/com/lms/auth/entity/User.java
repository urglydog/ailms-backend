package com.lms.auth.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.Role;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Người dùng hệ thống — gốc của mọi quyền truy cập.
 *
 * <p>{@code passwordHash} NULLABLE có chủ đích: người đăng nhập bằng Google OAuth2
 * không có mật khẩu (BR-AUTH-01). Đừng giả định field này luôn có giá trị.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    /** Bcrypt cost >= 10 (BR-AUTH-01). NULL khi authProvider = GOOGLE. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.STUDENT;

    /** LOCAL hoac GOOGLE. */
    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider = "LOCAL";

    /** Ngôn ngữ lồng tiếng mặc định của người dùng. */
    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "vi-VN";

    /**
     * Admin khóa tài khoản -> FALSE. Khi đó BẮT BUỘC thu hồi toàn bộ refresh
     * token của người dùng này (BR-AUTH-04).
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
