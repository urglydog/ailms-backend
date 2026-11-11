package com.lms.common.enums;

/**
 * Loại dòng trong sổ cái thu nhập giảng viên (11/11/2026, Epic hoàn tiền – chi trả).
 * {@code amount} của dòng có dấu: EARNING/PAYOUT_RELEASE dương, REFUND_REVERSAL/PAYOUT_RESERVE âm.
 *
 * <p>Dùng bởi: InstructorLedgerEntry</p>
 */
public enum LedgerEntryType {
    /** Thu nhập từ 1 đơn đã PAID (dương). */
    EARNING,
    /** Trừ lại thu nhập khi đơn bị hoàn tiền (âm). */
    REFUND_REVERSAL,
    /** Giữ chỗ số tiền giảng viên yêu cầu rút (âm). */
    PAYOUT_RESERVE,
    /** Trả lại số tiền giữ chỗ khi yêu cầu rút bị hủy/từ chối/thất bại (dương). */
    PAYOUT_RELEASE,
    /** Điều chỉnh tay có lý do (admin). */
    ADJUSTMENT
}
