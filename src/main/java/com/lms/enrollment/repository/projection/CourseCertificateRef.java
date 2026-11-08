package com.lms.enrollment.repository.projection;

/** Mã chứng chỉ của học viên cho một khóa (lấy theo lô). */
public record CourseCertificateRef(Long courseId, String certificateCode) {
}
