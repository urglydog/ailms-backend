package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Tài nguyên không tồn tại. Dùng chung cho mọi entity:
 * {@code throw new ResourceNotFoundException("Course", id)}.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resourceType + " khong ton tai: " + identifier);
    }
}
