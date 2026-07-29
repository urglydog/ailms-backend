package com.lms.auth.service;

import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.QuotaExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Xử lý nghiệp vụ OTP theo BR-AUTH-02.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_ATTEMPT_PREFIX = "otp_attempt:";
    private static final String OTP_RESEND_RATE_PREFIX = "otp_rate:";

    public void generateAndSendOtp(String email) {
        String rateKey = OTP_RESEND_RATE_PREFIX + email;
        
        // Kiểm tra giới hạn gửi: Tối đa 3 lần / giờ (BR-AUTH-02)
        String attemptsStr = redisTemplate.opsForValue().get(rateKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        
        if (attempts >= 3) {
            throw new QuotaExceededException("Bạn đã vượt quá giới hạn gửi OTP (3 lần/giờ). Vui lòng thử lại sau.", 3L);
        }

        // Tăng đếm số lần gửi (nếu chưa có thì set TTL 1 giờ)
        if (attempts == 0) {
            redisTemplate.opsForValue().set(rateKey, "1", Duration.ofHours(1));
        } else {
            redisTemplate.opsForValue().increment(rateKey);
        }

        // Sinh OTP ngẫu nhiên 6 số
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        
        // Lưu OTP vào Redis với TTL 5 phút (BR-AUTH-02)
        String otpKey = OTP_PREFIX + email;
        redisTemplate.opsForValue().set(otpKey, otp, Duration.ofMinutes(5));
        
        // Reset số lần nhập sai
        redisTemplate.delete(OTP_ATTEMPT_PREFIX + email);

        // Gửi email
        emailService.sendOtpEmail(email, otp);
    }

    public void verifyOtp(String email, String inputOtp) {
        String otpKey = OTP_PREFIX + email;
        String attemptKey = OTP_ATTEMPT_PREFIX + email;

        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            throw new BusinessRuleViolationException("Mã OTP đã hết hạn hoặc không tồn tại.");
        }

        if (!storedOtp.equals(inputOtp)) {
            // Tăng số lần nhập sai
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, Duration.ofMinutes(5));
            
            // Nhập sai tối đa 5 lần -> mã bị vô hiệu hóa (BR-AUTH-02)
            if (attempts != null && attempts >= 5) {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptKey);
                throw new BusinessRuleViolationException("Bạn đã nhập sai mã OTP 5 lần. Mã đã bị vô hiệu hóa.");
            }
            throw new BusinessRuleViolationException("Mã OTP không chính xác. Bạn còn " + (5 - attempts) + " lần thử.");
        }

        // Hợp lệ -> Xóa mã OTP (chỉ dùng 1 lần)
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptKey);
    }
}
