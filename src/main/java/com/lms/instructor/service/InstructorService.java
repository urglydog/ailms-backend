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
 * "Trở thành Giảng viên" kiểu Udemy (15/09/2026, thiết kế lại — thay UC41 "Admin duyệt yêu cầu
 * nâng cấp Giảng viên" cũ). Xem docblock {@link InstructorVerification} về phạm vi/giới hạn.
 */
@Service
@RequiredArgsConstructor
public class InstructorService {

    private final UserRepository userRepository;
    private final InstructorVerificationRepository verificationRepository;
    private final StorageService storageService;
    private final Tika tika = new Tika();

    /**
     * Nâng cấp vai trò STUDENT -> INSTRUCTOR NGAY LẬP TỨC, không cần Admin duyệt.
     *
     * <p>Access token hiện tại của client vẫn mang role STUDENT cũ cho tới khi hết hạn hoặc gọi
     * lại {@code POST /api/v1/auth/refresh} — {@code AuthService.refreshToken} đọc lại
     * {@code Role} mới nhất từ DB trước khi cấp token mới. FE BẮT BUỘC gọi refresh ngay sau khi
     * API này trả 200 để quyền Giảng viên có hiệu lực tức thì trên giao diện.
     */
    @Transactional
    public void becomeInstructor(String email) {
        User user = requireUser(email);
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

    /** Chỉ chính chủ tài khoản xem được thông tin mình đã nộp — xem docblock entity về bảo mật ảnh CCCD. */
    @Transactional(readOnly = true)
    public Res getMy(String email) {
        User user = requireUser(email);
        InstructorVerification verification = verificationRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("InstructorVerification", user.getId()));
        return toRes(verification);
    }

    /** BR-VERIFY-01 — chỉ nộp được đúng 1 LẦN/tài khoản. */
    @Transactional
    public Res submit(String email, String idNumber, String addressText, Boolean contentOwnershipConfirmed, MultipartFile file) {
        User user = requireUser(email);
        if (verificationRepository.existsByUser_Id(user.getId())) {
            throw new BusinessRuleViolationException("Tài khoản của bạn đã xác minh định danh trước đó rồi.");
        }
        if (idNumber == null || idNumber.isBlank()) {
            throw new InvalidRequestException("Vui lòng nhập số CCCD/CMND.");
        }
        if (addressText == null || addressText.isBlank()) {
            throw new InvalidRequestException("Vui lòng nhập địa chỉ thường trú.");
        }
        if (!Boolean.TRUE.equals(contentOwnershipConfirmed)) {
            throw new InvalidRequestException("Bạn cần xác nhận quyền sở hữu nội dung trước khi gửi.");
        }

        String photoUrl = uploadIdPhoto(user.getId(), file);

        InstructorVerification verification = new InstructorVerification();
        verification.setUser(user);
        verification.setIdNumber(idNumber.trim());
        verification.setAddressText(addressText.trim());
        verification.setContentOwnershipConfirmed(true);
        verification.setIdPhotoUrl(photoUrl);
        verification.setVerifiedAt(LocalDateTime.now());

        return toRes(verificationRepository.save(verification));
    }

    /**
     * Ảnh CCCD (15/09/2026) — cùng khuôn Tika-sniff thật (không tin đuôi file) như
     * {@code CourseService.uploadThumbnail}/{@code UserService.uploadAvatar}. Lưu trên CÙNG
     * bucket B2 công khai (xem docblock {@link InstructorVerification} về hạn chế bảo mật đã
     * biết), key ngẫu nhiên UUID để không đoán được đường dẫn.
     */
    private String uploadIdPhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Vui lòng chọn ảnh CCCD/CMND.");
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

    private Res toRes(InstructorVerification verification) {
        return new Res(
                verification.getId(),
                verification.getIdNumber(),
                verification.getIdPhotoUrl(),
                verification.getAddressText(),
                verification.getContentOwnershipConfirmed(),
                verification.getVerifiedAt()
        );
    }
}
