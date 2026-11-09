package com.lms.enrollment.entity;

/**
 * Trạng thái kiểm duyệt của {@link CourseReview} (03/10/2026, mở rộng — xem
 * UpComming_Plan.md Sprint 2 mục 6 Refined AC).
 */
public enum ReviewModerationStatus {
    /** Hiển thị công khai bình thường. */
    VISIBLE,
    /** Đã bị ẩn (do AI tự động hoặc Admin thủ công) — xem {@code moderationReason}. */
    HIDDEN,
    /** Giảng viên đã Report, bị ẩn tạm thời, đang chờ Admin duyệt. */
    PENDING_REPORT
}
