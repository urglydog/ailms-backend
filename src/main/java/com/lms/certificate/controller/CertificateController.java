package com.lms.certificate.controller;

import com.lms.certificate.dto.CertificateDto.Res;
import com.lms.certificate.dto.CertificateDto.VerifyRes;
import com.lms.certificate.service.CertificateService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /** "Chứng chỉ của tôi" — danh sách của chính học viên đang đăng nhập. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<Res>> getMine(Principal principal) {
        return ResponseEntity.ok(certificateService.getMyCertificates(principal.getName()));
    }

    /** BR-CERT-06 — trang xác thực công khai, KHÔNG cần đăng nhập (đăng ký ở
     * {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}: {@code /api/v1/certificates/verify/**}). */
    @GetMapping("/verify/{certificateCode}")
    public ResponseEntity<VerifyRes> verify(@PathVariable String certificateCode) {
        return ResponseEntity.ok(certificateService.verify(certificateCode));
    }

    @GetMapping("/{certificateCode}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Res> getDetail(Principal principal, @PathVariable String certificateCode) {
        return ResponseEntity.ok(certificateService.getCertificateDetail(principal.getName(), certificateCode));
    }

    /** PDF chỉ tải được từ tài khoản chủ sở hữu (BR-CERT-06/09) — render on-demand + cache B2. */
    @GetMapping("/{certificateCode}/pdf")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> getPdf(Principal principal, @PathVariable String certificateCode) {
        byte[] pdf = certificateService.getPdf(principal.getName(), certificateCode);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"chung-chi-" + certificateCode + ".pdf\"")
                .body(pdf);
    }
}
