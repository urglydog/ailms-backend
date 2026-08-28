package com.lms.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Điểm duy nhất biến exception thành HTTP response.
 *
 * <p>Trả về {@link ProblemDetail} (RFC 7807) — chuẩn có sẵn của Spring Boot 3, nên
 * dự án <b>không</b> tự định nghĩa class response envelope riêng. Frontend đọc
 * {@code detail} để hiện thông báo và {@code code} để phân nhánh xử lý.
 *
 * <p>Bảng ánh xạ status theo nghiệp vụ:
 * {@code Skills/CodeSkills/05_AIPoweredLMS/ai-agent/context/api.md}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bắt toàn bộ cây {@link DomainException} bằng một handler duy nhất — mỗi lỗi tự
     * khai status của nó, nên thêm lỗi nghiệp vụ mới không phải sửa file này.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("Loi nghiep vu [{}] tai {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), ex.getCode(), request);
    }

    /** Lỗi Bean Validation trên {@code @Valid @RequestBody} — trả kèm lỗi từng field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));

        ProblemDetail problem = build(HttpStatus.BAD_REQUEST,
                "Du lieu gui len khong hop le", "VALIDATION_FAILED", request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * `@PreAuthorize` từ chối quyền (vd. đúng role nhưng sai ownership, hoặc thiếu role).
     * Không bắt riêng thì rơi xuống lưới an toàn cuối cùng và trả nhầm 500.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Ban khong co quyen thuc hien hanh dong nay", "ACCESS_DENIED", request);
    }

    /** Vượt dung lượng upload — BR-CHUNK-01 (video 2 GB), BR-UPLOAD-01 (tài liệu 50 MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "File vuot qua dung luong cho phep", "FILE_TOO_LARGE", request);
    }

    /** Path Variable type mismatch (e.g. string passed to Long id) */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, org.springframework.beans.TypeMismatchException.class})
    public ProblemDetail handleTypeMismatch(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Sai kieu du lieu tham so", "TYPE_MISMATCH", request);
    }

    /**
     * Lưới an toàn cuối cùng. Log đầy đủ stack trace nhưng <b>không</b> trả chi tiết
     * nội bộ ra ngoài — tránh lộ thông tin hệ thống cho client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Loi khong mong doi tai {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Da xay ra loi khong mong doi. Vui long thu lai sau.", "INTERNAL_ERROR", request);
    }

    private ProblemDetail build(HttpStatus status, String detail, String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://lms.local/errors/" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", LocalDateTime.now().toString());
        return problem;
    }
}
