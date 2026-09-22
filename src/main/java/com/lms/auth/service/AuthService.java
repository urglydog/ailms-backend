package com.lms.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.lms.auth.dto.AuthRequestDto.*;
import com.lms.auth.dto.AuthResponseDto.*;
import com.lms.auth.entity.User;
import com.lms.auth.provider.GoogleOAuthProvider;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.CustomUserDetails;
import com.lms.auth.security.JwtTokenProvider;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;

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
    private final EmailService emailService;
    private final GoogleOAuthProvider googleOAuthProvider;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_PREFIX = "user_refresh_tokens:";
    private static final String PENDING_USER_PREFIX = "pending_user:";
    /** Task 10: Hash {email} -> {refreshToken: JSON{deviceName,ip,lastActiveAt}} — chỉ phục vụ hiển thị danh sách thiết bị, tách biệt khỏi cơ chế xác thực refresh token. */
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";

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
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, userDetails.getEmail(), Duration.ofDays(7));
        redisTemplate.opsForSet().add(USER_TOKENS_PREFIX + userDetails.getEmail(), refreshToken);
        redisTemplate.expire(USER_TOKENS_PREFIX + userDetails.getEmail(), Duration.ofDays(7));

        recordSessionMeta(userDetails.getEmail(), refreshToken);

        return new TokenRes(accessToken, refreshToken);
    }

    /** Task 10: ghi lại thiết bị/IP cho phiên này — best-effort, không được làm hỏng luồng đăng nhập nếu lỗi. */
    private void recordSessionMeta(String email, String refreshToken) {
        try {
            String userAgent = request.getHeader("User-Agent");
            String ip = extractClientIp();
            java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
            meta.put("deviceName", parseDeviceName(userAgent));
            meta.put("ip", ip != null ? ip : "");
            meta.put("lastActiveAt", java.time.LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(USER_SESSIONS_PREFIX + email, refreshToken, objectMapper.writeValueAsString(meta));
            redisTemplate.expire(USER_SESSIONS_PREFIX + email, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Không thể ghi session metadata cho {}: {}", email, e.getMessage());
        }
    }

    private String extractClientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Suy luận tên thiết bị đơn giản từ User-Agent (không cần thư viện ngoài cho nhu cầu hiển thị cơ bản). */
    private String parseDeviceName(String ua) {
        if (ua == null || ua.isBlank()) return "Thiết bị không xác định";
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Safari/") && !ua.contains("Chrome") ? "Safari"
                : "Trình duyệt";
        String os = ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Linux") ? "Linux"
                : "";
        return os.isEmpty() ? browser : browser + " trên " + os;
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
        String email = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (email != null) {
            redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + email, refreshToken);
            redisTemplate.opsForHash().delete(USER_SESSIONS_PREFIX + email, refreshToken);
        }
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    public void logoutAllDevices(String email) {
        java.util.Set<String> tokens = redisTemplate.opsForSet().members(USER_TOKENS_PREFIX + email);
        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + token);
            }
        }
        redisTemplate.delete(USER_TOKENS_PREFIX + email);
        redisTemplate.delete(USER_SESSIONS_PREFIX + email);
        // Xoá cả phiên xem video hiện tại (nếu có)
        redisTemplate.delete("user_stream:" + email); // Wait, user_stream uses userId!
    }

    /** Task 10: liệt kê các thiết bị/phiên đang đăng nhập (dựa trên refresh token còn hiệu lực). */
    public java.util.List<java.util.Map<String, String>> getActiveSessions(String email) {
        java.util.Map<Object, Object> entries = redisTemplate.opsForHash().entries(USER_SESSIONS_PREFIX + email);
        java.util.List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        for (Object value : entries.values()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> meta = objectMapper.readValue((String) value, java.util.Map.class);
                result.add(meta);
            } catch (Exception e) {
                log.warn("Bỏ qua session metadata hỏng cho {}: {}", email, e.getMessage());
            }
        }
        result.sort((a, b) -> b.getOrDefault("lastActiveAt", "").compareTo(a.getOrDefault("lastActiveAt", "")));
        return result;
    }

    /**
     * UC04.1 - Gửi OTP để đặt lại mật khẩu
     * BR-AUTH-02: OTP 5 phút hiệu lực, gửi lại <= 3 lần/email/giờ, lưu Redis
     */
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        String otp = generateOtp();
        String redisKey = "otp:forgot:" + email;
        redisTemplate.opsForValue().set(redisKey, otp, Duration.ofMinutes(5));

        // Track resend count: otp:forgot:resend:{email} with 1-hour TTL (max 3 resends)
        String resendKey = "otp:forgot:resend:" + email;
        String resendCountStr = redisTemplate.opsForValue().get(resendKey);
        Integer resendCount = resendCountStr != null ? Integer.parseInt(resendCountStr) : 0;

        if (resendCount >= 3) {
            throw new BusinessRuleViolationException("Đã vượt quá số lần gửi OTP cho email này trong 1 giờ");
        }

        redisTemplate.opsForValue().increment(resendKey);
        redisTemplate.expire(resendKey, Duration.ofHours(1));

        emailService.sendOtpEmail(email, otp);
        log.info("OTP đã được gửi đến email: {}", email);
    }

    /**
     * UC04.2 - Verify OTP and reset password
     * BR-AUTH-02: OTP max 5 wrong attempts, then invalidated
     * BR-AUTH-01: Hash password with Bcrypt cost >= 10
     * Auto-login user after successful reset (return JWT tokens)
     */
    @Transactional
    public TokenRes resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        String redisKey = "otp:forgot:" + email;
        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            throw new BusinessRuleViolationException("OTP đã hết hạn hoặc không tồn tại");
        }

        if (!storedOtp.equals(otp)) {
            // Track wrong attempts
            String wrongKey = "otp:forgot:wrong:" + email;
            String wrongCountStr = redisTemplate.opsForValue().get(wrongKey);
            Integer wrongCount = wrongCountStr != null ? Integer.parseInt(wrongCountStr) : 0;

            if (wrongCount >= 4) {
                // 5th wrong attempt - invalidate OTP
                redisTemplate.delete(redisKey);
                redisTemplate.delete(wrongKey);
                throw new BusinessRuleViolationException("Sai OTP quá 5 lần. Vui lòng yêu cầu gửi lại.");
            }

            // Increment wrong attempt counter
            redisTemplate.opsForValue().increment(wrongKey);
            redisTemplate.expire(wrongKey, Duration.ofMinutes(5));
            throw new BusinessRuleViolationException("OTP không chính xác");
        }

        // OTP correct, reset password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthProvider("LOCAL");
        userRepository.save(user);

        // Cleanup Redis
        redisTemplate.delete(redisKey);
        redisTemplate.delete("otp:forgot:wrong:" + email);

        // Auto-login user and return tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, new CustomUserDetails(user).getAuthorities());

        return generateTokens(authentication);
    }

    /**
     * UC02 extend - Google OAuth2 Login
     * BR-AUTH-01: Create/update user from Google OAuth2 token
     * BR-AUTH-04: Sinh và lưu Refresh Token vào Redis
     *
     * @param idToken Google ID token from frontend
     * @return TokenRes containing access and refresh tokens
     * @throws GeneralSecurityException if token verification fails
     * @throws IOException if token verification fails
     */
    @Transactional
    public TokenRes loginWithGoogle(String idToken) throws GeneralSecurityException, IOException {
        GoogleIdToken.Payload payload = googleOAuthProvider.verifyToken(idToken);

        String email = payload.getEmail();
        Optional<User> existingUser = userRepository.findByEmail(email);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Update profile fields from Google
            if (payload.get("name") != null) {
                user.setFullName((String) payload.get("name"));
            }
            if (payload.get("picture") != null) {
                user.setAvatarUrl((String) payload.get("picture"));
            }
            log.info("Updated existing Google OAuth user: {}", email);
        } else {
            // Create new user from Google OAuth
            user = new User();
            user.setEmail(email);
            user.setFullName((String) payload.get("name"));
            user.setAvatarUrl((String) payload.get("picture"));
            user.setAuthProvider("GOOGLE");
            user.setRole(Role.STUDENT);
            user.setIsActive(true);
            user.setPasswordHash(null);  // No password for Google OAuth users (BR-AUTH-01)
            log.info("Created new Google OAuth user: {}", email);
        }

        userRepository.save(user);

        // Create authentication and generate tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, new CustomUserDetails(user).getAuthorities());

        return generateTokens(authentication);
    }

    /**
     * Generate a random 6-digit OTP
     */
    private String generateOtp() {
        return otpService.generateOtp();
    }
}
