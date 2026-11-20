package com.lms.invoice.controller;

import com.lms.invoice.service.PublicInvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mở thẳng PDF chứng từ từ mã QR/liên kết in trên file (không cần đăng nhập). Một nội dung duy nhất cho mọi người mở — đúng bản đã gửi cho người nhận.
 * Mọi lỗi trả cùng 404 rỗng; vượt tần suất trả 429 rỗng. Xem {@link PublicInvoiceService}.
 */
@RestController
@RequestMapping("/api/v1/public/invoices")
@RequiredArgsConstructor
public class PublicInvoiceController {

    private final PublicInvoiceService publicInvoiceService;

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> open(@PathVariable String token, HttpServletRequest http) {
        PublicInvoiceService.Result r = publicInvoiceService.open(token, clientIp(http));
        return switch (r.outcome()) {
            case OK -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
                    .header("X-Robots-Tag", "noindex, nofollow")
                    .header("Referrer-Policy", "no-referrer")
                    .cacheControl(CacheControl.noStore())
                    .body(r.pdf());
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        };
    }

    private static String clientIp(HttpServletRequest http) {
        return com.lms.common.ratelimit.RateLimitFilter.clientIp(http);
    }
}
