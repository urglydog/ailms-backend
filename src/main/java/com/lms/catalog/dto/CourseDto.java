package com.lms.catalog.dto;

import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseDto {

    public record CreateReq(
            @NotBlank(message = "Tiêu đề khóa học không được để trống")
            String title,

            String description,

            @NotNull(message = "Danh mục không được để trống")
            Long categoryId,

            String level,

            @NotNull(message = "Giá không được để trống")
            @DecimalMin(value = "0", message = "Giá không được âm")
            BigDecimal price
    ) {}

    public record UpdateReq(
            @NotBlank(message = "Tiêu đề khóa học không được để trống")
            String title,

            String description,

            String thumbnailUrl,

            @NotNull(message = "Danh mục không được để trống")
            Long categoryId,

            String level,

            @NotNull(message = "Giá không được để trống")
            @DecimalMin(value = "0", message = "Giá không được âm")
            BigDecimal price
    ) {}

    public record RejectReq(
            @NotBlank(message = "Lý do từ chối không được để trống")
            @Size(min = 20, message = "Lý do từ chối phải có ít nhất 20 ký tự")
            String reason
    ) {}

    /** "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — {@code password} bắt buộc khi
     * {@code visibility == PRIVATE_PASSWORD} VÀ chưa từng đặt mật khẩu; để trống thì giữ
     * nguyên mật khẩu cũ (cho phép đổi các field khác mà không bắt nhập lại mật khẩu). */
    public record VisibilityUpdateReq(
            @NotNull(message = "Quyền riêng tư không được để trống")
            CourseVisibility visibility,
            String password
    ) {}

    public record InviteReq(
            @NotBlank(message = "Email không được để trống")
            String email
    ) {}

    public record SummaryRes(
            Long id,
            String title,
            String slug,
            CourseStatus status,
            String thumbnailUrl,
            String categoryName,
            BigDecimal price,
            Boolean isFree,
            BigDecimal avgRating,
            Integer totalLessons,
            LocalDateTime createdAt,
            /** % hồ sơ khóa học đã điền đủ (19/09/2026, mở rộng — giao diện tham khảo Udemy
             * "Hoàn thành khóa học của bạn" ở trang danh sách): tiêu đề/mô tả/ảnh bìa/≥1
             * chương/≥1 bài học sẵn sàng — KHÔNG tính điều kiện xác minh định danh (BR-VERIFY-01,
             * đó là điều kiện CẤP TÀI KHOẢN, không phải nội dung riêng của khóa này). */
            Integer completionPercent
    ) {}

    public record DetailRes(
            Long id,
            String title,
            String slug,
            String description,
            String thumbnailUrl,
            String level,
            BigDecimal price,
            Boolean isFree,
            CourseStatus status,
            String rejectReason,
            Integer resubmitCount,
            Long categoryId,
            String categoryName,
            Long instructorId,
            String instructorName,
            List<ChapterDto.Res> chapters,
            List<String> missingConditions,
            boolean canSubmit,
            /** "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026). */
            CourseVisibility visibility,
            /** {@code true} nếu đã từng đặt mật khẩu — KHÔNG bao giờ trả mật khẩu/hash thật. */
            boolean hasEnrollPassword,
            /** Chia doanh thu 2 mức (20/09/2026) — dựng liên kết giới thiệu
             * {@code /courses/{slug}?ref={referralCode}}. Chỉ trả về ở đây (response cho CHÍNH
             * chủ khóa học), không lộ qua bất kỳ API công khai nào khác. */
            String referralCode
    ) {}
}
