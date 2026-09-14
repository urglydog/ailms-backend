package com.lms.auth.dto;

import com.lms.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class UserDto {
    public record UserRes(
            Long id,
            String email,
            String fullName,
            String avatarUrl,
            Role role,
            String authProvider,
            String preferredLanguage,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** "View public profile" (14/09/2026, mở rộng) — 2 công tắc tách riêng, xem {@code User}. */
            Boolean coursesPublic,
            Boolean wishlistPublic
    ) {}

    public record UpdateUserReq(
            @NotBlank(message = "Họ tên không được để trống")
            String fullName,

            @NotNull(message = "Quyền (Role) không được để trống")
            Role role,

            @NotNull(message = "Trạng thái không được để trống")
            Boolean isActive
    ) {}

    public record UpdateMyProfileReq(
            String fullName,
            String avatarUrl,
            String preferredLanguage
    ) {}

    /** Bật/tắt hiển thị công khai từng mục trên "View public profile" (14/09/2026, mở rộng). */
    public record UpdatePrivacyReq(
            @NotNull(message = "coursesPublic không được để trống")
            Boolean coursesPublic,

            @NotNull(message = "wishlistPublic không được để trống")
            Boolean wishlistPublic
    ) {}

    /**
     * 1 khóa hiển thị trên "View public profile" — đủ dữ liệu để render thẻ card kiểu Udemy
     * (thumbnail, giá, sao đánh giá) mà không cần gọi thêm API chi tiết khóa học.
     */
    public record PublicCourseRes(
            Long courseId,
            String title,
            String slug,
            String thumbnailUrl,
            java.math.BigDecimal price,
            Boolean isFree,
            java.math.BigDecimal avgRating,
            Long reviewCount
    ) {}

    /**
     * "View public profile" (14/09/2026, mở rộng ngoài đặc tả gốc) — hồ sơ công khai của 1
     * người dùng bất kỳ (Student hoặc Instructor), xem được KHÔNG cần đăng nhập. {@code courses}/
     * {@code wishlist} là {@code null} (không phải mảng rỗng) khi chủ tài khoản đã ẩn mục đó —
     * FE phân biệt "ẩn" (null) với "công khai nhưng chưa có gì" (mảng rỗng) qua đó.
     */
    public record PublicProfileRes(
            Long id,
            String fullName,
            String avatarUrl,
            Role role,
            LocalDateTime memberSince,
            List<PublicCourseRes> courses,
            List<PublicCourseRes> wishlist
    ) {}
}
