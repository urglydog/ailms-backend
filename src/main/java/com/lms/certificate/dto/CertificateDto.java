package com.lms.certificate.dto;

import com.lms.common.enums.CertificateStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CertificateDto {

    /** "Chứng chỉ của tôi" (danh sách + chi tiết) — chủ sở hữu xem/tải/chia sẻ. */
    public record Res(
            String certificateCode,
            Long courseId,
            String courseTitle,
            String courseSlug,
            String studentName,
            BigDecimal courseHours,
            String instructorName,
            LocalDateTime completedAt,
            LocalDateTime issuedAt,
            CertificateStatus status,
            String verifyUrl
    ) {}

    /** Trang xác thực công khai (`/verify/:certificateCode`, BR-CERT-06) — KHÔNG có email, KHÔNG
     * có link tải PDF. {@code found=false} nghĩa là mã không tồn tại (khác REVOKED — REVOKED vẫn
     * "found" nhưng status khác ACTIVE). (26/09/2026, mở rộng) — đủ field để FE vẽ lại NGUYÊN hình
     * ảnh chứng chỉ (không chỉ 1 dòng xác nhận chữ) khi {@code status=ACTIVE}; các field này vốn
     * đã in công khai ngay trên mặt chứng chỉ nên không phát sinh rò rỉ thông tin riêng tư mới. */
    public record VerifyRes(
            boolean found,
            String certificateCode,
            String studentName,
            String courseTitle,
            BigDecimal courseHours,
            String instructorName,
            LocalDateTime completedAt,
            CertificateStatus status,
            String verifyUrl
    ) {
        public static VerifyRes notFound() {
            return new VerifyRes(false, null, null, null, null, null, null, null, null);
        }
    }

    /** Hiển thị ở trang hồ sơ công khai (`/u/{userId}`) — chỉ chứng chỉ ACTIVE. (26/09/2026, sửa
     * lỗi) — trước đây có {@code courseThumbnailUrl} để FE hiện ảnh bìa khóa học ở thẻ, nay đổi
     * sang hiện NGUYÊN hình chứng chỉ (giống {@code Res}) nên bỏ field đó, thêm đủ field cần cho
     * {@code CertificatePreview}. */
    public record PublicRes(
            String certificateCode,
            String courseTitle,
            String courseCategoryName,
            String studentName,
            BigDecimal courseHours,
            String instructorName,
            LocalDateTime completedAt,
            LocalDateTime issuedAt,
            CertificateStatus status,
            String verifyUrl
    ) {}
}
