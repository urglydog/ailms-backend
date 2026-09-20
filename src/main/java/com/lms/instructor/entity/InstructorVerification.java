package com.lms.instructor.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Xác minh định danh Giảng viên (15/09/2026, thiết kế lại — thay UC41 "Admin duyệt yêu cầu
 * nâng cấp Giảng viên" bằng nâng cấp NGAY khi bấm nút, xem {@code InstructorService}).
 *
 * <p><b>BR-VERIFY-01</b>: bắt buộc hoàn tất đúng 1 LẦN DUY NHẤT/tài khoản, trước khi gửi khóa
 * học ĐẦU TIÊN đi duyệt — không lặp lại cho các khóa học sau. Thực hiện bằng cách kiểm tra "đã
 * tồn tại bản ghi cho user này chưa" ({@code existsByUser_Id}) thay vì đếm số khóa học đã gửi
 * trước đó.
 *
 * <p><b>Ngoài phạm vi đồ án</b>: hệ thống chỉ THU THẬP thông tin định danh (số CCCD, địa chỉ) —
 * KHÔNG gọi bất kỳ API eKYC/định danh điện tử của chính phủ nào để xác thực thật. Việc "xác
 * minh" ở đây chỉ dừng ở mức thu thập đủ hồ sơ.
 *
 * <p>(20/09/2026, theo yêu cầu) — bỏ hẳn bước upload ảnh CCCD ({@code idPhotoUrl}, cột
 * {@code id_photo_url}): rủi ro lưu ảnh giấy tờ tùy thân trên bucket B2 công khai dùng chung
 * cho toàn hệ thống (không có hạ tầng presigned-URL/bucket riêng cho dữ liệu nhạy cảm) không
 * đáng để đổi lấy giá trị nghiệp vụ của bước này trong phạm vi đồ án — form giờ chỉ còn số
 * CCCD/CMND, địa chỉ, và xác nhận quyền sở hữu nội dung.
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

    @Column(name = "address_text", nullable = false, length = 500)
    private String addressText;

    @Column(name = "content_ownership_confirmed", nullable = false)
    private Boolean contentOwnershipConfirmed = false;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;
}
