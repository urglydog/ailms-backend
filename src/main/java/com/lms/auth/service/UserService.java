package com.lms.auth.service;

import com.lms.auth.dto.UserDto.PublicCourseRes;
import com.lms.auth.dto.UserDto.PublicProfileRes;
import com.lms.auth.dto.UserDto.UpdateMyProfileReq;
import com.lms.auth.dto.UserDto.UpdatePrivacyReq;
import com.lms.auth.dto.UserDto.UpdateUserReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.AccountLockRegistry;
import com.lms.catalog.entity.Course;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.certificate.service.CertificateService;
import com.lms.common.enums.CertificateStatus;
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
import org.springframework.cache.annotation.Cacheable;
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
    private final CertificateRepository certificateRepository;
    private final CertificateService certificateService;
    private final AccountLockRegistry accountLockRegistry;
    private final Tika tika = new Tika();

    private static final java.util.Set<String> ADMIN_SORTABLE = java.util.Set.of("id", "fullName", "email", "role", "createdAt");
    private static final int ADMIN_MAX_PAGE_SIZE = 100;

    /**
     * Trang Quản lý người dùng (Admin): tìm/lọc/sắp xếp/phân trang phía server (hiệu năng đợt 3).
     * {@code filter}: ALL | STUDENT | INSTRUCTOR | ADMIN | LOCKED | SPAM. {@code page} đếm từ 1 như giao diện.
     * Cột sắp xếp chỉ nhận danh sách cho phép; kích thước trang tối đa {@value #ADMIN_MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserRes> searchUsers(String query, String filter, String sortField,
                                                                       String sortOrder, int page, int size) {
        String q = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        String f = filter == null ? "ALL" : filter.toUpperCase(java.util.Locale.ROOT);
        com.lms.common.enums.Role role = switch (f) {
            case "STUDENT" -> com.lms.common.enums.Role.STUDENT;
            case "INSTRUCTOR" -> com.lms.common.enums.Role.INSTRUCTOR;
            case "ADMIN" -> com.lms.common.enums.Role.ADMIN;
            default -> null;
        };
        // Set.of(...).contains(null) ném NPE — cột rỗng coi như không chọn.
        boolean sortable = sortField != null && ADMIN_SORTABLE.contains(sortField);
        String field = sortable ? sortField : "createdAt";
        org.springframework.data.domain.Sort.Direction dir = "asc".equalsIgnoreCase(sortOrder) && sortable
                ? org.springframework.data.domain.Sort.Direction.ASC
                : org.springframework.data.domain.Sort.Direction.DESC;
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1),
                Math.min(Math.max(1, size), ADMIN_MAX_PAGE_SIZE),
                org.springframework.data.domain.Sort.by(dir, field).and(org.springframework.data.domain.Sort.by("id")));
        long qId = query != null && query.trim().matches("\\d{1,18}") ? Long.parseLong(query.trim()) : -1L;
        return userRepository.searchForAdmin(q, qId, role, "LOCKED".equals(f), "SPAM".equals(f), pageable).map(this::mapToRes);
    }

    /** Số người dùng cho các tab lọc của trang Quản lý người dùng — 3 truy vấn đếm, không tải danh sách. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> countUsers() {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        long all = 0;
        for (String key : List.of("STUDENT", "INSTRUCTOR", "ADMIN")) counts.put(key, 0L);
        for (Object[] row : userRepository.countGroupedByRole()) {
            long n = ((Number) row[1]).longValue();
            counts.put(String.valueOf(row[0]), n);
            all += n;
        }
        counts.put("ALL", all);
        counts.put("LOCKED", userRepository.countByIsActiveFalse());
        counts.put("SPAM", userRepository.countByIsSpamFlaggedTrue());
        return counts;
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
        // dùng để xin cấp lại token mới nhờ cơ chế chặn ở AuthService.refreshToken.
        // (23/10/2026, sửa bug thật) — riêng access token CŨ vẫn sống tới khi hết hạn tự nhiên
        // nếu không chủ động thu hồi ở đây, xem docblock AccountLockRegistry.
        //
        // Cascade ĐỐI XỨNG 2 CHIỀU (23/10/2026, sửa theo phản hồi test thật — bản trước chỉ
        // cascade 1 chiều lúc khóa, lúc mở khóa phải tự gỡ AI/spam thủ công riêng, admin phản
        // ánh "chưa đồng bộ"): khóa tài khoản → tự khóa AI + đánh dấu spam; mở khóa tài khoản →
        // tự mở AI + bỏ đánh dấu spam luôn, xem docblock AccountRestrictionService.
        if (req.isActive()) {
            accountLockRegistry.unlock(id);
            user.setIsAiLocked(false);
            user.setIsSpamFlagged(false);
            user.setSpamFlagReason(null);
        } else {
            accountLockRegistry.lock(id);
            user.setIsAiLocked(true);
            if (!Boolean.TRUE.equals(user.getIsSpamFlagged())) {
                user.setSpamFlagReason("Tự động theo khóa tài khoản");
            }
            user.setIsSpamFlagged(true);
        }

        return mapToRes(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        // Không xóa cứng để giữ toàn vẹn khóa ngoại (khóa học, hóa đơn, v.v)
        // Chỉ khóa tài khoản (Soft Delete / Deactivate)
        user.setIsActive(false);
        accountLockRegistry.lock(id);
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
    // TTL 10 phút, không evict thủ công (29/09/2026) — dữ liệu ghép từ 4 nguồn (user/
    // enrollments/wishlist/certificates), chấp nhận trễ vài phút thay vì phải nhớ evict cache
    // này ở EnrollmentService/WishlistService/CertificateService (nhiều điểm ghi, dễ sót).
    // Không có tham số theo requester nên an toàn cache theo userId (khác bug PRIVATE_INVITE ở
    // CoursePublicService.getBySlug — method đó có nhánh quyền theo requester, method này thì
    // không, mọi người xem cùng 1 nội dung công khai).
    @Cacheable(value = "publicProfile", key = "#userId")
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

        List<com.lms.certificate.dto.CertificateDto.PublicRes> certificates = certificateRepository
                .findByStudent_IdAndStatusOrderByIssuedAtDesc(userId, CertificateStatus.ACTIVE).stream()
                .map(certificateService::toPublicRes)
                .toList();

        return new PublicProfileRes(
                user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getHeadline(), user.getBio(),
                user.getRole(), user.getCreatedAt(), courses, wishlist, certificates
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
                user.getWishlistPublic(),
                user.getIsAiLocked(),
                user.getIsSpamFlagged(),
                user.getSpamFlagReason()
        );
    }
}
