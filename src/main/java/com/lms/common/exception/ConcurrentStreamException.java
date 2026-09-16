package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi khi phát hiện có thiết bị khác đang phát video trên cùng tài khoản.
 */
public class ConcurrentStreamException extends DomainException {

    public ConcurrentStreamException(String message) {
        super(HttpStatus.CONFLICT, "CONCURRENT_STREAM_DETECTED", message);
    }
}
