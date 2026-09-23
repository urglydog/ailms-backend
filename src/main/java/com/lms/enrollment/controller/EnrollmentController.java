package com.lms.enrollment.controller;

import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.service.EnrollmentService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Khóa học của tôi" — không nằm trong {@code PUBLIC_GET_ENDPOINTS} nên mặc định cần đăng nhập. */
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /** (20/09/2026, sửa lỗi) — trước đây chỉ `hasRole('STUDENT')`, khiến tài khoản đã nâng lên
     * Giảng viên KHÔNG còn xem được các khóa đã mua từ lúc còn là Học viên. Trở thành Giảng
     * viên không tước quyền sở hữu nội dung đã mua trước đó — 1 tài khoản có thể vừa dạy vừa
     * học, xem `InstructorService.becomeInstructor` (chỉ đổi role, không đụng Enrollment). */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<Res>> getMine(Principal principal) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(principal.getName()));
    }

    @PostMapping("/free/{courseId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Void> enrollFreeCourse(
            Principal principal,
            @org.springframework.web.bind.annotation.PathVariable Long courseId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String password) {
        enrollmentService.enrollFreeCourse(principal.getName(), courseId, password);
        return ResponseEntity.ok().build();
    }

    /** UpComming_Plan.md A1 — tải PDF chứng chỉ hoàn thành, sinh on-the-fly, chỉ khi đã hoàn thành 100%. */
    @GetMapping("/{courseId}/certificate")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> getCertificate(
            Principal principal,
            @org.springframework.web.bind.annotation.PathVariable Long courseId) {
        byte[] pdf = enrollmentService.generateCertificatePdf(principal.getName(), courseId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"certificate-" + courseId + ".pdf\"")
                .body(pdf);
    }
}
