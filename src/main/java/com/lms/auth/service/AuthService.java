package com.lms.auth.service;

import com.lms.auth.dto.AuthRequestDto.*;
import com.lms.auth.dto.AuthResponseDto.*;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.CustomUserDetails;
import com.lms.auth.security.JwtTokenProvider;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;
    private final StringRedisTemplate redisTemplate;

    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String PENDING_USER_PREFIX = "pending_user:";

    /**
     * B1 Đăng ký: Lưu tạm thông tin user vào Redis chờ xác thực OTP.
     * Cùng lúc gọi OtpService sinh mã gửi qua mail.
     */
    public void register(RegisterReq req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ConflictException("Email đã được sử dụng");
        }

        // Lưu thông tin đăng ký thô vào Redis (ví dụ: chuỗi nối bằng | )
        String pendingData = req.fullName() + "|" + passwordEncoder.encode(req.password());
        redisTemplate.opsForValue().set(PENDING_USER_PREFIX + req.email(), pendingData, Duration.ofMinutes(10));

        otpService.generateAndSendOtp(req.email());
    }

    /**
     * B2 Đăng ký: Xác nhận OTP và lưu User chính thức vào MySQL
     */
    @Transactional
    public void verifyRegistration(VerifyOtpReq req) {
        otpService.verifyOtp(req.email(), req.otp());

        String pendingData = redisTemplate.opsForValue().get(PENDING_USER_PREFIX + req.email());
        if (pendingData == null) {
            throw new BusinessRuleViolationException("Yêu cầu đăng ký đã hết hạn. Vui lòng đăng ký lại.");
        }

        String[] parts = pendingData.split("\\|");
        
        User user = new User();
        user.setEmail(req.email());
        user.setFullName(parts[0]);
        user.setPasswordHash(parts[1]);
        user.setRole(Role.STUDENT);
        user.setAuthProvider("LOCAL");
        user.setIsActive(true);

        userRepository.save(user);
        redisTemplate.delete(PENDING_USER_PREFIX + req.email());
    }

    /**
     * BR-AUTH-03: Kiểm tra khóa tài khoản và đếm login fail
     * BR-AUTH-04: Sinh và lưu Refresh Token vào Redis
     */
    public TokenRes login(LoginReq req) {
        String failKey = LOGIN_FAIL_PREFIX + req.email();

        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        if (failCount >= 5) {
            throw new LockedException("Tài khoản đã bị tạm khóa do nhập sai mật khẩu 5 lần. Vui lòng thử lại sau 15 phút.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );

            // Đăng nhập thành công -> Xóa đếm login fail (BR-AUTH-03)
            redisTemplate.delete(failKey);

            return generateTokens(authentication);

        } catch (BadCredentialsException ex) {
            // Tăng số lần fail
            Long currentFails = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, Duration.ofMinutes(15));
            
            if (currentFails != null && currentFails >= 5) {
                // Có thể tích hợp thêm EmailService gửi cảnh báo tại đây
                throw new LockedException("Bạn đã nhập sai mật khẩu 5 lần. Tài khoản bị tạm khóa 15 phút.");
            }
            throw new BusinessRuleViolationException("Email hoặc mật khẩu không chính xác. Bạn còn " + (5 - currentFails) + " lần thử.");
        }
    }

    private TokenRes generateTokens(Authentication authentication) {
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        
        // Lưu Refresh Token vào Redis với TTL 7 ngày (BR-AUTH-04)
        // Lưu key là token, value là email. Có thể lưu thêm set user_refresh_tokens để thu hồi
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, userDetails.getEmail(), Duration.ofDays(7));

        return new TokenRes(accessToken, refreshToken);
    }

    public TokenRes refreshToken(RefreshTokenReq req) {
        String email = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + req.refreshToken());
        
        if (email == null || !jwtTokenProvider.validateToken(req.refreshToken())) {
            throw new BusinessRuleViolationException("Refresh Token không hợp lệ hoặc đã hết hạn.");
        }

        // Lấy lại UserDetails để cấp token mới
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
                
        if (!user.getIsActive()) {
            throw new LockedException("Tài khoản đã bị khóa.");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, new CustomUserDetails(user).getAuthorities());

        // Xóa token cũ, sinh cặp token mới (Rotation)
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + req.refreshToken());
        return generateTokens(authentication);
    }

    public void logout(String refreshToken) {
        // Thu hồi refresh token hiện tại bằng cách xóa khỏi Redis
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }
}
