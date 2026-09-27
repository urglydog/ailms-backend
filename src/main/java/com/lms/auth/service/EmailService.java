package com.lms.auth.service;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${lms.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@lms.local");
            message.setTo(toEmail);
            message.setSubject("Mã xác thực OTP - AI Powered LMS");
            message.setText("Mã xác thực của bạn là: " + otp + "\n"
                    + "Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.");

            mailSender.send(message);
            log.info("Đã gửi OTP đến email: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email đến {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Không thể gửi email OTP, vui lòng thử lại sau.");
        }
    }

    /** WishlistPriceDropService (26/09/2026, tính năng mới) — gọi từ job nền (consumer Redis),
     * không phải từ request HTTP trực tiếp. KHÔNG throw khi lỗi (khác {@link #sendOtpEmail}) —
     * đây là thông báo "best-effort", 1 email lỗi không được làm hỏng cả batch xử lý giảm giá
     * (xem vòng lặp try/catch từng học viên ở {@code WishlistPriceDropService.processJob}). */
    public void sendPriceDropEmail(String toEmail, String courseTitle, String courseSlug, BigDecimal oldPrice, BigDecimal newPrice) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@lms.local");
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
