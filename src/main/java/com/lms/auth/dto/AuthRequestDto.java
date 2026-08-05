package com.lms.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequestDto {

    public record RegisterReq(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không hợp lệ")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            @Size(min = 6, message = "Mật khẩu phải từ 6 ký tự trở lên")
            String password,

            @NotBlank(message = "Họ và tên không được để trống")
            String fullName
    ) {}

    public record LoginReq(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không hợp lệ")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            String password
    ) {}

    public record VerifyOtpReq(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không hợp lệ")
            String email,

            @NotBlank(message = "OTP không được để trống")
            String otp
    ) {}

    public record RefreshTokenReq(
            @NotBlank(message = "Refresh Token không được để trống")
            String refreshToken
    ) {}

    public record ForgotPasswordReq(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không hợp lệ")
            String email
    ) {}
}
