package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Tài nguyên từng tồn tại nhưng nay không còn khả dụng.
 *
 * <p>Chủ yếu cho BR-DUB-11: nguồn video bị gỡ hoặc chuyển riêng tư sau khi đã
 * sinh audio — bài học chuyển sang {@code UNAVAILABLE}, file audio vẫn giữ.
 */
public class ResourceGoneException extends DomainException {

    public ResourceGoneException(String message) {
        super(HttpStatus.GONE, "RESOURCE_GONE", message);
    }
}
