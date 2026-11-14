package com.lms.common.enums;

import java.util.Set;

/**
 * Mã lý do chuẩn khi admin quyết định hoàn tiền (14/11/2026). Mỗi mã chỉ đi với một số chế độ trách nhiệm — admin không thể chọn
 * "giảng viên chịu toàn bộ" chỉ vì cảm tính: phải là một vi phạm có tên, kèm minh chứng và đã cho giảng viên cơ hội phản hồi.
 */
public enum RefundReasonCode {
    // Chuẩn: không ai có lỗi, giảng viên trả lại đúng phần đã nhận.
    BUYER_REMORSE(RefundResponsibility.STANDARD),
    OTHER(RefundResponsibility.STANDARD),
    // Nền tảng gánh: lỗi từ phía hệ thống.
    TECHNICAL_ISSUE(RefundResponsibility.PLATFORM_COMPENSATES),
    DOUBLE_CHARGE(RefundResponsibility.PLATFORM_COMPENSATES),
    NOT_ENROLLED(RefundResponsibility.PLATFORM_COMPENSATES),
    PLATFORM_FAULT(RefundResponsibility.PLATFORM_COMPENSATES),
    // Giảng viên chịu toàn bộ: vi phạm có bằng chứng.
    COPYRIGHT_VIOLATION(RefundResponsibility.INSTRUCTOR_FAULT),
    CONTENT_MISMATCH(RefundResponsibility.INSTRUCTOR_FAULT),
    PROHIBITED_CONTENT(RefundResponsibility.INSTRUCTOR_FAULT);

    private final RefundResponsibility responsibility;

    RefundReasonCode(RefundResponsibility responsibility) {
        this.responsibility = responsibility;
    }

    /** Chế độ trách nhiệm duy nhất mà mã này được phép đi cùng. */
    public RefundResponsibility responsibility() {
        return responsibility;
    }

    public static final Set<RefundReasonCode> FAULT_CODES =
            Set.of(COPYRIGHT_VIOLATION, CONTENT_MISMATCH, PROHIBITED_CONTENT);
}
