package com.lms.auth.controller;

import com.lms.auth.dto.AuthRequestDto.ChangePasswordReq;
import com.lms.auth.dto.AuthResponseDto.MessageRes;
import com.lms.auth.dto.UserDto.PublicProfileRes;
import com.lms.auth.dto.UserDto.UpdateMyProfileReq;
import com.lms.auth.dto.UserDto.UpdatePrivacyReq;
import com.lms.auth.dto.UserDto.UpdateUserReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.security.CustomUserDetails;
import com.lms.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.lms.auth.service.AuthService authService;

    /**
     * Lấy danh sách toàn bộ người dùng (Chỉ dành cho Admin).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserRes>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Lấy thông tin cá nhân của người đang đăng nhập (Tất cả Role đều gọi được).
     */
    @GetMapping("/me")
    public ResponseEntity<UserRes> getMyProfile(java.security.Principal principal) {
        return ResponseEntity.ok(userService.getUserByEmail(principal.getName()));
    }

    /**
     * Đăng xuất khỏi tất cả các thiết bị khác (Task 10).
     */
    @PostMapping("/me/logout-all")
    public ResponseEntity<MessageRes> logoutAllDevices(java.security.Principal principal) {
        authService.logoutAllDevices(principal.getName());
        return ResponseEntity.ok(new MessageRes("Đã đăng xuất khỏi tất cả các thiết bị."));
    }

    /**
     * Danh sách thiết bị/phiên đang đăng nhập của người dùng hiện tại (Task 10).
     */
    @GetMapping("/me/sessions")
    public ResponseEntity<List<java.util.Map<String, String>>> getMySessions(java.security.Principal principal) {
        return ResponseEntity.ok(authService.getActiveSessions(principal.getName()));
    }

    /**
     * UC05 - Đổi mật khẩu cho người dùng hiện tại.
     * Yêu cầu xác thực mật khẩu hiện tại trước khi cho phép đổi.
     */
    @PutMapping("/me/password")
    public ResponseEntity<MessageRes> changePassword(
            java.security.Principal principal,
            @Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(principal.getName(), req.currentPassword(), req.newPassword());
        return ResponseEntity.ok(new MessageRes("Đổi mật khẩu thành công"));
    }

    /**
     * UC06 - Cập nhật thông tin cá nhân cho người dùng hiện tại.
     * Cho phép cập nhật fullName, avatarUrl, preferredLanguage (tất cả tùy chọn).
     */
    @PutMapping("/me")
    public ResponseEntity<UserRes> updateMyProfile(
            java.security.Principal principal,
            @Valid @RequestBody UpdateMyProfileReq req) {
        UserRes result = userService.updateMyProfile(principal.getName(), req);
        return ResponseEntity.ok(result);
    }

    /**
     * Đổi ảnh đại diện cho người dùng hiện tại (14/09/2026, mở rộng ngoài đặc tả gốc) —
     * cùng khuôn với {@code CourseController.uploadThumbnail}.
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserRes> uploadMyAvatar(
            java.security.Principal principal, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(principal.getName(), file));
    }

    /**
     * "View public profile" (14/09/2026, mở rộng) — bật/tắt hiển thị công khai khóa học đã
     * học / wishlist của người dùng hiện tại.
     */
    @PutMapping("/me/privacy")
    public ResponseEntity<UserRes> updateMyPrivacy(
            java.security.Principal principal, @Valid @RequestBody UpdatePrivacyReq req) {
        return ResponseEntity.ok(userService.updatePrivacy(principal.getName(), req));
    }

    /**
     * "View public profile" (14/09/2026, mở rộng) — hồ sơ công khai của 1 người dùng bất kỳ,
     * xem được KHÔNG cần đăng nhập (nằm trong {@code PUBLIC_GET_ENDPOINTS} của SecurityConfig).
     */
    @GetMapping("/{id}/public-profile")
    public ResponseEntity<PublicProfileRes> getPublicProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }

    /**
     * Lấy thông tin một người dùng theo ID (Chỉ dành cho Admin).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRes> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Cập nhật thông tin người dùng (Admin có thể đổi Role hoặc Khóa tài khoản).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRes> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserReq req) {
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /**
     * Xóa mềm (Khóa) tài khoản người dùng (Chỉ dành cho Admin).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
