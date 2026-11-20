package com.lms.invoice.service;

import com.lms.common.ratelimit.RateLimiter;
import com.lms.common.service.SystemConfigService;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mở thẳng PDF chứng từ bằng liên kết/QR trên file (20/11/2026) — đường công khai DUY NHẤT của module chứng từ, nên áp đủ các lớp bảo vệ:
 * (1) mã chỉ nhận đúng dạng base64url 43 ký tự (danh sách trắng) trước khi chạm DB; (2) tra bằng tham số, không ghép chuỗi; (3) mọi lỗi (sai dạng, không có,
 * đã tắt) đều trả cùng một kết quả rỗng nên không dò được chứng từ nào tồn tại; (4) giới hạn tần suất theo IP, từ chối khi Redis lỗi; (5) công tắc tắt khẩn cấp
 * {@code PUBLIC_INVOICE_LINK_ENABLED}; (6) PDF sinh từ dữ liệu bất biến, kích thước nhỏ, không đọc tệp theo đường dẫn người dùng gửi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicInvoiceService {

    static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    static final int PER_MINUTE_PER_IP = 20;
    static final String CFG_ENABLED = "PUBLIC_INVOICE_LINK_ENABLED";

    private final InvoiceQueryService queryService;
    private final SystemConfigService config;
    private final RateLimiter rateLimiter;

    /** Kết quả: {@link Outcome#OK} kèm PDF, {@link Outcome#NOT_FOUND} (mọi lý do không có), hoặc {@link Outcome#RATE_LIMITED} (kể cả khi bộ đếm lỗi). */
    public Result open(String token, String clientIp) {
        if (!allowed(clientIp)) {
            return new Result(Outcome.RATE_LIMITED, null);
        }
        if (config.getInt(CFG_ENABLED, 1) != 1 || token == null || !TOKEN.matcher(token).matches()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Optional<byte[]> pdf = queryService.pdfByAccessToken(token);
        return pdf.map(b -> new Result(Outcome.OK, b)).orElseGet(() -> new Result(Outcome.NOT_FOUND, null));
    }

    private boolean allowed(String clientIp) {
        try {
            return rateLimiter.hit("pub-invoice", clientIp == null || clientIp.isBlank() ? "unknown" : clientIp, PER_MINUTE_PER_IP) == 0;
        } catch (RuntimeException e) {
            log.warn("Bộ đếm tần suất lỗi, từ chối truy cập công khai chứng từ: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public enum Outcome { OK, NOT_FOUND, RATE_LIMITED }

    public record Result(Outcome outcome, byte[] pdf) {}
}
