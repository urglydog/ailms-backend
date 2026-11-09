package com.lms.announcement.dto;

import com.lms.common.enums.AnnouncementAudience;
import com.lms.common.enums.AnnouncementDispatchStatus;
import com.lms.common.enums.AnnouncementSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class SystemAnnouncementDto {

    public record CreateReq(
            @NotBlank(message = "Tiêu đề không được để trống") String title,
            @NotBlank(message = "Nội dung không được để trống") String content,
            @NotNull(message = "Vui lòng chọn mức độ") AnnouncementSeverity severity,
            @NotNull(message = "Vui lòng chọn phạm vi người nhận") AnnouncementAudience audience,
            /** Bắt buộc khi {@code audience = SPECIFIC_USER}, kiểm ở tầng service (khác các ràng
             * buộc field đơn thuần Bean Validation lo được). */
            Long targetUserId,
            /** Chỉ có ý nghĩa khi {@code severity = HIGH} (thời điểm banner tự ẩn) — null = hiển
             * thị vô thời hạn. */
            LocalDateTime expiresAt
    ) {}

    /** Lịch sử thông báo đã gửi, cho Admin xem lại tiến độ phát tán. */
    public record Res(
            Long id,
            String title,
            String content,
            AnnouncementSeverity severity,
            AnnouncementAudience audience,
            Long targetUserId,
            LocalDateTime expiresAt,
            AnnouncementDispatchStatus dispatchStatus,
            Integer totalRecipients,
            Integer sentCount,
            String createdByAdminName,
            LocalDateTime createdAt
    ) {}

    /** Banner cố định đầu trang — public, không cần đăng nhập, KHÔNG có field nào khác ngoài nội
     * dung hiển thị (không lộ audience/targetUserId/thống kê nội bộ). */
    public record BannerRes(
            Long id,
            String title,
            String content,
            AnnouncementSeverity severity
    ) {}
}
