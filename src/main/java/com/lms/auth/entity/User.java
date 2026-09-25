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

    @Column(name = "headline", length = 255)
    private String headline;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

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

    /**
     * Admin khóa quyền gọi AI của user (Cong viec 5).
     * Khi TRUE, user bị từ chối mọi yêu cầu sinh học liệu / chat AI.
     */
    @Column(name = "is_ai_locked", nullable = false)
    private Boolean isAiLocked = false;

    /**
     * Auto-ban bằng AI (25/09/2026) — job định kỳ phát hiện tín hiệu bất thường (quota hết liên
     * tục nhiều ngày HOẶC tần suất request bất thường) chỉ tạo ĐỀ XUẤT khoá ở đây, KHÔNG tự khoá
     * ngay — Admin xem lý do rồi mới quyết định set {@code isAiLocked}. Human-in-the-loop, tránh
     * rủi ro "AI khoá nhầm" khi trình bày.
     */
    @Column(name = "ai_lock_proposed_at")
    private java.time.LocalDateTime aiLockProposedAt;

    @Column(name = "ai_lock_proposed_reason", length = 500)
    private String aiLockProposedReason;

    /**
     * "View public profile" kiểu Udemy (14/09/2026, mở rộng ngoài đặc tả gốc) — cho phép
     * người khác xem danh sách khóa học đã học / wishlist của mình. 2 công tắc TÁCH RIÊNG
     * (không dùng chung 1 cờ) vì học viên có thể muốn khoe khóa đã học nhưng giấu wishlist
     * (tránh lộ ý định mua sắp tới), hoặc ngược lại.
     */
    @Column(name = "courses_public", nullable = false)
    private Boolean coursesPublic = true;

    @Column(name = "wishlist_public", nullable = false)
    private Boolean wishlistPublic = true;
}
