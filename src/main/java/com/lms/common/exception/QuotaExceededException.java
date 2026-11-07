package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Vượt hạn ngạch nghiệp vụ.
 *
 * <p>BR-DUB-06 (15/30 job lồng tiếng mỗi ngày) · BR-MAT-08 (6 lượt học liệu) ·
 * BR-TUTOR-04 (30 tin nhắn) · BR-DISCOVERY-01 (15 tin/IP/giờ).
 */
public class QuotaExceededException extends DomainException {

    public QuotaExceededException(String quotaName, long limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", "Da dat gioi han " + quotaName + " (" + limit + ")");
    }

    /** Thông báo tuỳ biến (có dấu, nói rõ thời điểm được hỏi lại) — hiển thị thẳng cho người dùng. */
    public QuotaExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message);
    }
}
