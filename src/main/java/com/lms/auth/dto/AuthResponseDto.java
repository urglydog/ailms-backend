package com.lms.auth.dto;

public class AuthResponseDto {

    public record TokenRes(
            String accessToken,
            String refreshToken
    ) {}
    
    public record MessageRes(
            String message
    ) {}

    /** Link reset mật khẩu Admin tự gửi thủ công cho user khi email/OTP không nhận được. */
    public record ResetLinkRes(
            String resetLink,
            java.time.LocalDateTime expiresAt
    ) {}
}
