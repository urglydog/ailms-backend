package com.lms.auth.service;

import com.lms.auth.dto.UserDto.PublicCourseRes;
import com.lms.auth.dto.UserDto.PublicProfileRes;
import com.lms.auth.dto.UserDto.UpdateMyProfileReq;
import com.lms.auth.dto.UserDto.UpdatePrivacyReq;
import com.lms.auth.dto.UserDto.UpdateUserReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final EnrollmentRepository enrollmentRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public List<UserRes> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserRes getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return mapToRes(user);
    }
    
    @Transactional(readOnly = true)
    public UserRes getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return mapToRes(user);
    }

    @Transactional
    public UserRes updateUser(Long id, UpdateUserReq req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        user.setFullName(req.fullName());
        user.setRole(req.role());
        user.setIsActive(req.isActive());
        
        // Nếu user bị khóa (isActive = false), refresh token hiện tại của user sẽ không thể
        // dùng để xin cấp lại token mới nhờ cơ chế chặn ở AuthService.refreshToken
        
        return mapToRes(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        // Không xóa cứng để giữ toàn vẹn khóa ngoại (khóa học, hóa đơn, v.v)
        // Chỉ khóa tài khoản (Soft Delete / Deactivate)
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * UC05 - Change Password: Verify current password and update to new password
     * Only for users with LOCAL auth provider (those who have a password hash)
     * Google OAuth users cannot change password (passwordHash is NULL)
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (user.getPasswordHash() == null) {
            throw new InvalidRequestException("Tài khoản này sử dụng đăng nhập Google. Không thể đổi mật khẩu.");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("Mật khẩu hiện tại không chính xác");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * UC06 - Update My Profile: Allow authenticated users to update their own profile
     * Users can update fullName, avatarUrl, and preferredLanguage (all optional)
     */
    @Transactional
    public UserRes updateMyProfile(String email, UpdateMyProfileReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName());
        }
        if (req.avatarUrl() != null && !req.avatarUrl().isBlank()) {
            user.setAvatarUrl(req.avatarUrl());
        }
        if (req.headline() != null && !req.headline().isBlank()) {
            user.setHeadline(req.headline());
        }
        if (req.bio() != null && !req.bio().isBlank()) {
            user.setBio(req.bio());
        }
        if (req.preferredLanguage() != null && !req.preferredLanguage().isBlank()) {
            user.setPreferredLanguage(req.preferredLanguage());
        }

        userRepository.save(user);
        return mapToRes(user);
    }

    /** "View public profile" (14/09/2026, mở rộng) — bật/tắt hiển thị công khai từng mục. */
    @Transactional
    public UserRes updatePrivacy(String email, UpdatePrivacyReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        user.setCoursesPublic(req.coursesPublic());
        user.setWishlistPublic(req.wishlistPublic());

        userRepository.save(user);
        return mapToRes(user);
    }

    /**
     * Đổi ảnh đại diện (14/09/2026, mở rộng) — cùng khuôn với
     * {@code CourseService.uploadThumbnail} (Tika phát hiện MIME thật, giới hạn 5MB, chỉ
     * JPEG/PNG/WEBP, key ngẫu nhiên trên B2 qua {@link StorageService}).
     */
    @Transactional
    public UserRes uploadAvatar(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (file.isEmpty()) {
            throw new InvalidRequestException("File ảnh trống");
        }
        long maxBytes = 5L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("Ảnh đại diện vượt quá 5MB");
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

        String key = "avatars/" + user.getId() + "/" + UUID.randomUUID() + "." + extension;
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được ảnh lên kho lưu trữ: " + e.getMessage());
        }

        user.setAvatarUrl(url);
        userRepository.save(user);
        return mapToRes(user);
    }

    /**
     * "View public profile" (14/09/2026, mở rộng ngoài đặc tả gốc) — hồ sơ công khai, xem được
     * KHÔNG cần đăng nhập (endpoint nằm trong {@code PUBLIC_GET_ENDPOINTS}). {@code courses}/
     * {@code wishlist} trả {@code null} khi chủ tài khoản đã tắt công khai mục đó — KHÔNG trả
     * mảng rỗng, để FE phân biệt được "đã ẩn" với "công khai nhưng chưa có gì".
     */
    @Transactional(readOnly = true)
    public PublicProfileRes getPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<PublicCourseRes> courses = Boolean.TRUE.equals(user.getCoursesPublic())
                ? enrollmentRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                        .map(Enrollment::getCourse)
                        .map(this::toPublicCourseRes)
                        .toList()
                : null;

        List<PublicCourseRes> wishlist = Boolean.TRUE.equals(user.getWishlistPublic())
                ? wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                        .map(WishlistItem::getCourse)
                        .map(this::toPublicCourseRes)
                        .toList()
                : null;

        return new PublicProfileRes(
                user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getHeadline(), user.getBio(),
                user.getRole(), user.getCreatedAt(), courses, wishlist
        );
    }

    private PublicCourseRes toPublicCourseRes(Course course) {
        long reviewCount = courseReviewRepository.countByCourse_IdAndIsHiddenFalse(course.getId());
        return new PublicCourseRes(
                course.getId(), course.getTitle(), course.getSlug(), course.getThumbnailUrl(),
                course.getPrice(), course.getIsFree(), course.getAvgRating(), reviewCount
        );
    }

    private UserRes mapToRes(User user) {
        return new UserRes(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getHeadline(),
                user.getBio(),
                user.getRole(),
                user.getAuthProvider(),
                user.getPreferredLanguage(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getCoursesPublic(),
                user.getWishlistPublic()
        );
    }
}
