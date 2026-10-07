package com.lms.auth.controller;

import com.lms.auth.dto.AuthRequestDto.*;
import com.lms.auth.dto.AuthResponseDto.*;
import com.lms.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
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

    @PostMapping("/oauth/google/callback")
    @ResponseStatus(HttpStatus.OK)
    public TokenRes loginWithGoogle(@Valid @RequestBody GoogleOAuthCallbackReq req) throws Exception {
        log.info("Google OAuth login callback received");
        return authService.loginWithGoogle(req.idToken());
    }

    /**
     * Luồng Google OAuth cho app mobile (Expo Go) — Google redirect về đây sau khi user đăng
     * nhập. BE đổi {@code code} lấy id_token, tái dùng {@code loginWithGoogle}, rồi 302 về app
     * kèm 1 mã dùng-1-lần (không bao giờ đặt JWT thật trực tiếp lên URL). Public endpoint vì
     * Google gọi trực tiếp từ trình duyệt, không có Bearer token nào ở bước này.
     */
    @GetMapping("/oauth/google/mobile-callback")
    public ResponseEntity<Void> googleMobileCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) throws Exception {
        String appRedirectUrl = authService.handleGoogleMobileCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appRedirectUrl)).build();
    }

    @PostMapping("/oauth/google/mobile-exchange")
    @ResponseStatus(HttpStatus.OK)
    public TokenRes exchangeGoogleMobileCode(@Valid @RequestBody GoogleMobileExchangeReq req) {
        return authService.exchangeGoogleMobileCode(req.code());
    }
}
