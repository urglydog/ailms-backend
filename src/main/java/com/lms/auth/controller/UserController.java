package com.lms.auth.controller;

import com.lms.auth.dto.AuthRequestDto.ChangePasswordReq;
import com.lms.auth.dto.AuthResponseDto.MessageRes;
import com.lms.auth.dto.UserDto.UpdateMyProfileReq;
import com.lms.auth.dto.UserDto.UpdateUserReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.security.CustomUserDetails;
import com.lms.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
