package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Vi phạm điều kiện nghiệp vụ có thể sửa được.
 *
 * <p>Ví dụ: gửi khóa học đi kiểm duyệt khi chưa đủ điều kiện (BR-COURSE-01) —
 * kèm danh sách điều kiện chưa đạt · vượt 2 bài Preview (BR-ENROLL-02) ·
 * vượt 5 file tài liệu (BR-UPLOAD-01).
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }
}
