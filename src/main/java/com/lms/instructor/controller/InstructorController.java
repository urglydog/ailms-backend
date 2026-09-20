package com.lms.instructor.controller;

import com.lms.instructor.dto.InstructorVerificationDto.Res;
import com.lms.instructor.dto.InstructorVerificationDto.StatusRes;
import com.lms.instructor.dto.InstructorVerificationDto.SubmitReq;
import com.lms.instructor.service.InstructorService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Đăng ký Giảng viên kiểu Udemy (15/09/2026) — thay thế {@code InstructorRequestController} cũ.
 *
 * <p>(19/09/2026) — khôi phục lại sau khi bị mất khỏi nhánh {@code feat/dubbing} trong lúc merge
 * PR #132 (`urglydog/feat/additional-features`); FE (hooks/useInstructor.ts, panel xác minh ở
 * trang Hồ sơ cá nhân) vẫn nguyên vẹn suốt thời gian đó, chỉ backend bị rớt.
 */
@RestController
@RequestMapping("/api/v1/instructor")
@RequiredArgsConstructor
public class InstructorController {

    private final InstructorService instructorService;

    /**
     * Nâng cấp vai trò NGAY LẬP TỨC, không cần Admin duyệt (BR-ROLE-04 mới).
     *
     * <p>Access token hiện tại của client vẫn mang role STUDENT cũ cho tới khi hết hạn hoặc
     * gọi lại {@code POST /api/v1/auth/refresh} — {@code AuthService.refreshToken} đọc lại
     * {@code Role} mới nhất từ DB trước khi cấp token mới. FE BẮT BUỘC gọi refresh ngay sau
     * khi API này trả 200 để quyền Giảng viên có hiệu lực tức thì trên giao diện.
     */
    @PostMapping("/become")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> becomeInstructor(Principal principal) {
        instructorService.becomeInstructor(principal.getName());
        return ResponseEntity.ok().build();
    }

    /** FE dùng để quyết định có chặn nút "Gửi duyệt" khóa học đầu tiên hay không (BR-VERIFY-01). */
    @GetMapping("/verification/status")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StatusRes> getVerificationStatus(Principal principal) {
        return ResponseEntity.ok(instructorService.getMyStatus(principal.getName()));
    }

    /** Chỉ chính chủ tài khoản xem được thông tin mình đã nộp — xem docblock entity. */
    @GetMapping("/verification/me")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Res> getMyVerification(Principal principal) {
        return ResponseEntity.ok(instructorService.getMy(principal.getName()));
    }

    @PostMapping("/verification")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Res> submitVerification(Principal principal, @RequestBody SubmitReq req) {
        return ResponseEntity.ok(instructorService.submit(
                principal.getName(), req.idNumber(), req.addressText(), req.contentOwnershipConfirmed()));
    }
}
