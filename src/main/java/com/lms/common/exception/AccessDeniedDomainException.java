package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Đã xác thực nhưng không có quyền trên tài nguyên cụ thể.
 *
 * <p>Dùng cho hai tình huống chính:
 * <ul>
 *   <li>Giảng viên sửa tài nguyên không phải của mình (BR-ROLE-01)</li>
 *   <li>Học viên truy cập nội dung khóa học chưa sở hữu (BR-ENROLL-02)</li>
 * </ul>
 * Tên có hậu tố {@code Domain} để không lẫn với
 * {@code org.springframework.security.access.AccessDeniedException}.
 */
public class AccessDeniedDomainException extends DomainException {

    public AccessDeniedDomainException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
