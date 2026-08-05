package com.lms.auth.controller;

import com.lms.auth.dto.AuthRequestDto.*;
import com.lms.auth.dto.AuthResponseDto.*;
import com.lms.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageRes register(@Valid @RequestBody RegisterReq req) {
        authService.register(req);
        return new MessageRes("OTP đã được gửi đến email của bạn.");
    }

    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageRes verifyRegistration(@Valid @RequestBody VerifyOtpReq req) {
        authService.verifyRegistration(req);
        return new MessageRes("Đăng ký tài khoản thành công!");
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public TokenRes login(@Valid @RequestBody LoginReq req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public TokenRes refreshToken(@Valid @RequestBody RefreshTokenReq req) {
        return authService.refreshToken(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public MessageRes logout(@Valid @RequestBody RefreshTokenReq req) {
        authService.logout(req.refreshToken());
        return new MessageRes("Đăng xuất thành công.");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public MessageRes forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        authService.forgotPassword(req.email());
        return new MessageRes("OTP đã được gửi đến email của bạn.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public TokenRes resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        return authService.resetPassword(req.email(), req.otp(), req.newPassword());
    }
}
