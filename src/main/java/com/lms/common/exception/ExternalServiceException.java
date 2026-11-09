package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Dịch vụ ngoài (AI Worker, cổng thanh toán...) không trả lời được — lỗi tạm thời,
 * không phải lỗi của người dùng.
 */
public class ExternalServiceException extends DomainException {

    public ExternalServiceException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_SERVICE_UNAVAILABLE", message);
    }
}
