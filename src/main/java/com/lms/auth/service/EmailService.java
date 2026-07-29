package com.lms.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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
}
