package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Xung đột trạng thái.
 *
 * <p>Ví dụ: đã đạt 10 bộ học liệu (BR-MAT-07) · chọn ngôn ngữ lồng tiếng trùng
 * ngôn ngữ gốc của video (BR-DUB-09) · đã tồn tại yêu cầu nâng cấp PENDING
 * (BR-ROLE-04).
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
