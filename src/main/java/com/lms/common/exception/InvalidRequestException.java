package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Dữ liệu đầu vào không hợp lệ về mặt nghiệp vụ.
 *
 * <p>Ví dụ: ngôn ngữ không có trong bảng {@code voice_mappings} đang active
 * (BR-DUB-07) · định dạng file không được phép (BR-UPLOAD-01).
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
