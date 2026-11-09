package com.lms.common.enums;

/**
 * Trạng thái job AI (BR-CHUNK-04, BR-DUB-10).
 * FAILED được retry tối đa 3 lần với exponential backoff.
 * SKIPPED (video không có lời thoại) TUYỆT ĐỐI KHÔNG retry.
 * CANCELLED — học viên chủ động huỷ giữa chừng (khác FAILED: không phải lỗi hệ thống,
 * không tính vào thống kê lỗi cho Admin, và KHÔNG cho retry lại — muốn lồng tiếng lại
 * thì bấm kích hoạt như một job mới).
 *
 * <p>Dùng bởi: AiJob, AiJobChunk</p>
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED
}
