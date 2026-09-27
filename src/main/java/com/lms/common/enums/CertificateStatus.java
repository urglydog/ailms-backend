package com.lms.common.enums;

/**
 * Trạng thái chứng chỉ hoàn thành khóa học (BR-CERT-07).
 * ACTIVE -> REVOKED (một chiều, Admin thu hồi khi phát hiện gian lận/vi phạm chính sách).
 * Trang xác thực công khai (`/verify/:certificateCode`) phải hiển thị rõ khi REVOKED, không
 * hiển thị như chứng chỉ hợp lệ.
 *
 * <p>Dùng bởi: Certificate</p>
 */
public enum CertificateStatus {
    ACTIVE,
    REVOKED
}
