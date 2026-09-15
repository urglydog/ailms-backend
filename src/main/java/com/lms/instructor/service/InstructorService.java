package com.lms.instructor.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.instructor.dto.InstructorVerificationDto.Res;
import com.lms.instructor.dto.InstructorVerificationDto.StatusRes;
import com.lms.instructor.entity.InstructorVerification;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Đăng ký Giảng viên kiểu Udemy (15/09/2026) — thay thế UC41 (Admin duyệt hồ sơ minh chứng
 * chuyên môn) bằng nâng cấp vai trò NGAY LẬP TỨC + 1 bước xác minh định danh chặn ở lần gửi
 * khóa học đầu tiên đi duyệt (BR-VERIFY-01, thực thi tại {@code CourseService.submitForReview}).
 *
 * <p>Xem thêm doc/01_ThietKeLai_GiangVien_ThanhToan_Coupon.md.
 */
@Service
@RequiredArgsConstructor
public class InstructorService {

    private final UserRepository userRepository;
    private final InstructorVerificationRepository verificationRepository;
    private final StorageService storageService;
    private final Tika tika = new Tika();

    /**
     * BR-ROLE-04 (mới) — không còn hàng đợi PENDING chờ Admin duyệt. Bấm là có hiệu lực ngay,
     * nhưng do JWT bake role tại thời điểm sinh token (xem {@code JwtTokenProvider}), quyền
     * Giảng viên chỉ thật sự dùng được ở access token KẾ TIẾP (FE phải gọi lại {@code /refresh}
     * sau khi upgrade thành công, xem ghi chú ở Controller).
     */
    @Transactional
    public void becomeInstructor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (user.getRole() != Role.STUDENT) {
            throw new BusinessRuleViolationException("Bạn đã là Giảng viên hoặc Quản trị viên rồi.");
        }

        user.setRole(Role.INSTRUCTOR);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public StatusRes getMyStatus(String email) {
        User user = requireUser(email);
        return new StatusRes(verificationRepository.existsByUser_Id(user.getId()));
    }

    /** Chỉ chính chủ tài khoản gọi được (Controller giới hạn qua {@code principal.getName()}). */
    @Transactional(readOnly = true)
    public Res getMy(String email) {
        User user = requireUser(email);
        return verificationRepository.findByUser_Id(user.getId())
                .map(InstructorService::toRes)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorVerification", user.getId()));
    }

    /**
     * BR-VERIFY-01 — thu thập, KHÔNG xác minh qua API bên ngoài. Chỉ nộp được đúng 1 lần/tài
     * khoản (ràng buộc UNIQUE trên {@code user}, chặn thêm ở tầng service để trả lỗi rõ ràng
     * thay vì lỗi constraint DB khó hiểu).
     */
    @Transactional
    public Res submit(String email, String idNumber, String addressText, Boolean contentOwnershipConfirmed, MultipartFile file) {
        User user = requireUser(email);

        if (verificationRepository.existsByUser_Id(user.getId())) {
            throw new BusinessRuleViolationException("Bạn đã hoàn tất xác minh thông tin định danh trước đó rồi, không thể nộp lại.");
        }
        if (!Boolean.TRUE.equals(contentOwnershipConfirmed)) {
            throw new InvalidRequestException("Bạn cần xác nhận cam kết quyền sở hữu nội dung.");
        }
        if (idNumber == null || idNumber.isBlank()) {
            throw new InvalidRequestException("Số CCCD không được để trống.");
        }
        if (addressText == null || addressText.isBlank()) {
            throw new InvalidRequestException("Địa chỉ không được để trống.");
        }

        String idPhotoUrl = uploadIdPhoto(user.getId(), file);

        InstructorVerification verification = new InstructorVerification();
        verification.setUser(user);
        verification.setIdNumber(idNumber.trim());
        verification.setAddressText(addressText.trim());
        verification.setContentOwnershipConfirmed(true);
        verification.setIdPhotoUrl(idPhotoUrl);
        verification.setVerifiedAt(LocalDateTime.now());

        return toRes(verificationRepository.save(verification));
    }

    /** Ảnh CCCD — cùng khuôn Tika + giới hạn 5MB đã dùng cho avatar/ảnh bìa khóa học. */
    private String uploadIdPhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File ảnh CCCD trống");
        }
        long maxBytes = 5L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("Ảnh CCCD vượt quá 5MB");
        }

        String detectedMime;
        try (InputStream sniff = file.getInputStream()) {
            detectedMime = tika.detect(sniff);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file ảnh: " + e.getMessage());
        }
        String extension = switch (detectedMime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new InvalidRequestException("Chỉ chấp nhận ảnh JPEG/PNG/WEBP");
        };

        String key = "instructor-verification/" + userId + "/" + UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            return storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được ảnh lên kho lưu trữ: " + e.getMessage());
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private static Res toRes(InstructorVerification v) {
        return new Res(
                v.getId(), v.getIdNumber(), v.getIdPhotoUrl(), v.getAddressText(),
                v.getContentOwnershipConfirmed(), v.getVerifiedAt());
    }
}
