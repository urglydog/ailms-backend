package com.lms.instructor.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Xác minh thông tin định danh Giảng viên (15/09/2026) — thay thế {@code InstructorRequest}
 * cũ (Admin duyệt hồ sơ minh chứng chuyên môn). Nâng cấp vai trò giờ diễn ra NGAY LẬP TỨC khi
 * học viên bấm nút (xem {@code InstructorService.becomeInstructor}); bản ghi này chỉ chặn
 * bước GỬI KHÓA HỌC ĐẦU TIÊN đi duyệt (BR-VERIFY-01), không chặn việc dạy.
 *
 * <p><b>BR-VERIFY-01:</b> hệ thống CHỈ thu thập và lưu trữ thông tin tự khai, KHÔNG gọi API
 * đối chiếu cơ sở dữ liệu quốc gia (dịch vụ eKYC chính thức cần hợp đồng cấp doanh nghiệp,
 * ngoài phạm vi đồ án). 1 bản ghi/tài khoản, không được nộp lại/sửa sau khi đã hoàn tất —
 * ràng buộc UNIQUE trên {@code user} đảm bảo điều này ở tầng DB.
 *
 * <p><b>Ảnh CCCD ({@code idPhotoUrl}) là dữ liệu nhạy cảm</b> — vẫn nằm trên CÙNG bucket B2
 * public như mọi file khác (hệ thống chưa có hạ tầng bucket private/signed-URL), nhưng
 * {@code InstructorVerificationService} CHỈ trả field này về cho chính chủ tài khoản qua
 * endpoint {@code GET /api/v1/instructor/verification/me} — không endpoint public/admin nào
 * khác lộ URL này ra ngoài. Giới hạn đã biết: URL vẫn kỹ thuật truy cập được nếu bị lộ link
 * (không có xác thực ở tầng lưu trữ), key dùng UUID ngẫu nhiên nên không đoán được.
 */
@Entity
@Table(name = "instructor_verifications")
@Getter
@Setter
public class InstructorVerification extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "id_number", nullable = false, length = 50)
    private String idNumber;

    @Column(name = "id_photo_url", nullable = false, length = 500)
    private String idPhotoUrl;

    @Column(name = "address_text", nullable = false, length = 500)
    private String addressText;

    @Column(name = "content_ownership_confirmed", nullable = false)
    private Boolean contentOwnershipConfirmed = false;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;
}
