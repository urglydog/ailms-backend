package com.lms.auth.service;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${lms.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${lms.mail.from-address:noreply@lms.local}")
    private String fromAddress;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Mã xác thực OTP - LinguaLearn");
            helper.setText(
                    "Mã xác thực của bạn là: " + otp + "\n"
                            + "Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.",
                    buildOtpEmailHtml(otp));

            mailSender.send(mimeMessage);
            log.info("Đã gửi OTP đến email: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email đến {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Không thể gửi email OTP, vui lòng thử lại sau.");
        }
    }

    /** Template HTML tối giản cho email OTP (27/09/2026) — trước đây dùng {@code SimpleMailMessage}
     * (text thuần) trông thiếu chuyên nghiệp. Theo đúng màu thương hiệu ở
     * {@code fe/tailwind.config.ts} (`accent` #2563EB, `ink` #0F172A, `ink-muted` #64748B), không
     * phụ thuộc template engine ngoài — chỉ 1 chuỗi HTML nội tuyến. */
    private String buildOtpEmailHtml(String otp) {
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;background:#ffffff;\">"
                + "<div style=\"text-align:center;margin-bottom:24px;\">"
                + "<span style=\"display:inline-block;width:36px;height:36px;background:#2563EB;border-radius:10px;color:#ffffff;font-weight:700;font-size:20px;line-height:36px;\">L</span>"
                + "<div style=\"margin-top:8px;font-size:18px;font-weight:700;color:#0F172A;\">LinguaLearn</div>"
                + "</div>"
                + "<h2 style=\"font-size:16px;color:#0F172A;text-align:center;margin:0 0 8px;\">Mã xác thực của bạn</h2>"
                + "<p style=\"font-size:14px;color:#64748B;text-align:center;margin:0 0 24px;\">Nhập mã bên dưới để hoàn tất xác thực tài khoản.</p>"
                + "<div style=\"background:#F1F5F9;border-radius:12px;padding:20px;text-align:center;margin-bottom:24px;\">"
                + "<span style=\"font-size:32px;font-weight:700;letter-spacing:8px;color:#2563EB;\">" + otp + "</span>"
                + "</div>"
                + "<p style=\"font-size:13px;color:#64748B;text-align:center;margin:0;\">Mã có hiệu lực trong <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>"
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:24px 0;\">"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0;\">Nếu bạn không yêu cầu mã này, hãy bỏ qua email này.</p>"
                + "</div>";
    }

    /** WishlistPriceDropService (26/09/2026, tính năng mới) — gọi từ job nền (consumer Redis),
     * không phải từ request HTTP trực tiếp. KHÔNG throw khi lỗi (khác {@link #sendOtpEmail}) —
     * đây là thông báo "best-effort", 1 email lỗi không được làm hỏng cả batch xử lý giảm giá
     * (xem vòng lặp try/catch từng học viên ở {@code WishlistPriceDropService.processJob}). */
    public void sendPriceDropEmail(String toEmail, String courseTitle, String courseSlug, BigDecimal oldPrice, BigDecimal newPrice) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("🔥 Khóa học trong danh sách yêu thích của bạn vừa giảm giá!");
            message.setText(String.format(
                    "Khóa học \"%s\" mà bạn đã lưu vào danh sách yêu thích vừa giảm giá từ %s xuống còn %s.%n%n"
                            + "Xem ngay: %s/courses/%s%n%n"
                            + "Nếu không muốn nhận email này nữa, bạn có thể bỏ khóa học khỏi danh sách yêu thích.",
                    courseTitle, formatVnd(oldPrice), formatVnd(newPrice), frontendBaseUrl, courseSlug));

            mailSender.send(message);
            log.info("Đã gửi email báo giảm giá đến {} cho khóa học \"{}\"", toEmail, courseTitle);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email báo giảm giá đến {}: {}", toEmail, e.getMessage());
        }
    }

    private static String formatVnd(BigDecimal amount) {
        return String.format("%,.0fđ", amount);
    }
}
