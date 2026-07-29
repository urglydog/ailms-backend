package com.lms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lớp cha cho mọi lỗi nghiệp vụ của LMS.
 *
 * <p>Mỗi lỗi tự khai HTTP status của nó, nên {@code GlobalExceptionHandler} chỉ cần
 * một {@code @ExceptionHandler} duy nhất cho cả cây — không phải thêm handler mỗi
 * lần có lỗi mới.
 *
 * <p>Tầng service ném lỗi thuộc cây này, <b>không</b> ném
 * {@code ResponseStatusException} hay {@code HttpStatus} trực tiếp: service không
 * được biết về tầng HTTP.
 *
 * <p>Bảng ánh xạ status theo nghiệp vụ nằm ở
 * {@code Skills/CodeSkills/05_AIPoweredLMS/ai-agent/context/api.md}.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Mã lỗi ổn định để frontend nhận diện, ví dụ {@code COURSE_NOT_FOUND}. */
    public String getCode() {
        return code;
    }
}
