package com.lms.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.lms.auth.dto.AuthResponseDto.TokenRes;
import com.lms.auth.entity.User;
import com.lms.auth.provider.GoogleOAuthProvider;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtTokenProvider;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.forgotPassword() - UC04.1
 * Tests OTP generation, storage, and resend rate limiting
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private OtpService otpService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private GoogleOAuthProvider googleOAuthProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void testForgotPassword_ShouldSendOtpAndStoreInRedis() {
        String email = "student@lms.local";
        String generatedOtp = "123456";

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:resend:" + email)).thenReturn(null);
        when(otpService.generateOtp()).thenReturn(generatedOtp);

        authService.forgotPassword(email);

        // Verify OTP was generated
        verify(otpService, times(1)).generateOtp();

        // Verify OTP was stored in Redis with 5-minute TTL
        verify(valueOperations, times(1)).set("otp:forgot:" + email, generatedOtp, Duration.ofMinutes(5));

        // Verify email was sent with the OTP
        verify(emailService, times(1)).sendOtpEmail(email, generatedOtp);

        // Verify resend count was incremented
        verify(valueOperations, times(1)).increment("otp:forgot:resend:" + email);
        verify(redisTemplate, times(1)).expire("otp:forgot:resend:" + email, Duration.ofHours(1));
    }

    @Test
    void testForgotPassword_ShouldThrowExceptionIfUserNotFound() {
        String email = "nonexistent@lms.local";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            authService.forgotPassword(email);
        });

        // Verify email service was never called
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void testForgotPassword_ShouldThrowExceptionIfResendLimitExceeded() {
        String email = "student@lms.local";
        String resendKey = "otp:forgot:resend:" + email;

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(resendKey)).thenReturn("3");

        assertThrows(BusinessRuleViolationException.class, () -> {
            authService.forgotPassword(email);
        });

        // Verify email service was never called when limit is exceeded
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void testForgotPassword_ShouldTrackResendCount() {
        String email = "student@lms.local";
        String generatedOtp = "654321";
        String resendKey = "otp:forgot:resend:" + email;

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(resendKey)).thenReturn("1");  // Already sent once
        when(otpService.generateOtp()).thenReturn(generatedOtp);

        authService.forgotPassword(email);

        // Verify resend count was incremented
        verify(valueOperations, times(1)).increment(resendKey);
        // Verify 1-hour TTL was set
        verify(redisTemplate, times(1)).expire(resendKey, Duration.ofHours(1));

        // Verify email was still sent (count is 1, limit is 3)
        verify(emailService, times(1)).sendOtpEmail(email, generatedOtp);
    }

    /**
     * UC04.2 - Reset Password: Verify OTP and reset password
     * Tests OTP validation, password hashing, and token generation
     */
    @Test
    void testResetPassword_ShouldValidateOtpAndResetPassword() {
        String email = "student@lms.local";
        String otp = "123456";
        String newPassword = "NewPassword123!";
        String hashedPassword = "$2a$10$hashedPassword";

        User user = new User();
        user.setEmail(email);
        user.setId(1L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:" + email)).thenReturn(otp);
        when(passwordEncoder.encode(newPassword)).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        TokenRes tokens = authService.resetPassword(email, otp, newPassword);

        assertNotNull(tokens);
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());

        // Verify OTP was deleted after successful reset
        verify(redisTemplate, times(1)).delete("otp:forgot:" + email);
        verify(redisTemplate, times(1)).delete("otp:forgot:wrong:" + email);

        // Verify password was encoded and user was saved
        verify(passwordEncoder, times(1)).encode(newPassword);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testResetPassword_ShouldFailOnWrongOtp() {
        String email = "student@lms.local";
        String correctOtp = "123456";
        String wrongOtp = "000000";

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:" + email)).thenReturn(correctOtp);
        when(valueOperations.get("otp:forgot:wrong:" + email)).thenReturn(null);

        assertThrows(BusinessRuleViolationException.class, () ->
            authService.resetPassword(email, wrongOtp, "NewPassword123!")
        );

        // Verify wrong attempts counter was incremented
        verify(valueOperations, times(1)).increment("otp:forgot:wrong:" + email);
        // Verify password was NOT changed
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testResetPassword_ShouldInvalidateOtpAfter5WrongAttempts() {
        String email = "student@lms.local";
        String correctOtp = "123456";
        String wrongOtp = "000000";

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:" + email)).thenReturn(correctOtp);
        when(valueOperations.get("otp:forgot:wrong:" + email)).thenReturn("4"); // 4 wrong attempts already

        assertThrows(BusinessRuleViolationException.class, () ->
            authService.resetPassword(email, wrongOtp, "NewPassword123!")
        );

        // Verify OTP was deleted due to too many wrong attempts
        verify(redisTemplate, times(1)).delete("otp:forgot:" + email);
        verify(redisTemplate, times(1)).delete("otp:forgot:wrong:" + email);
        // Verify password was NOT changed
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testResetPassword_ShouldThrowExceptionIfUserNotFound() {
        String email = "nonexistent@lms.local";
        String otp = "123456";
        String newPassword = "NewPassword123!";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            authService.resetPassword(email, otp, newPassword)
        );

        // Verify OTP validation was never attempted
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testResetPassword_ShouldThrowExceptionIfOtpExpired() {
        String email = "student@lms.local";
        String otp = "123456";
        String newPassword = "NewPassword123!";

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:" + email)).thenReturn(null); // OTP expired

        assertThrows(BusinessRuleViolationException.class, () ->
            authService.resetPassword(email, otp, newPassword)
        );

        // Verify password was NOT changed
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * UC02 extend - Google OAuth2 Login: Create new user if not exists
     * Tests Google token verification, user creation, and JWT generation
     */
    @Test
    void testLoginWithGoogle_ShouldCreateUserIfNotExists() throws GeneralSecurityException, IOException {
        String googleEmail = "user@gmail.com";
        String idToken = "valid-google-token";

        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn(googleEmail);
        when(payload.get("name")).thenReturn("Google User");
        when(payload.get("picture")).thenReturn("https://example.com/pic.jpg");
        when(googleOAuthProvider.verifyToken(idToken)).thenReturn(payload);

        when(userRepository.findByEmail(googleEmail)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        TokenRes tokens = authService.loginWithGoogle(idToken);

        assertNotNull(tokens);
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(googleEmail, savedUser.getEmail());
        assertEquals("Google User", savedUser.getFullName());
        assertEquals("https://example.com/pic.jpg", savedUser.getAvatarUrl());
        assertEquals("GOOGLE", savedUser.getAuthProvider());
        assertEquals(Role.STUDENT, savedUser.getRole());
        assertTrue(savedUser.getIsActive());
        assertNull(savedUser.getPasswordHash());
    }

    @Test
    void testLoginWithGoogle_ShouldUpdateExistingUser() throws GeneralSecurityException, IOException {
        String googleEmail = "existing@gmail.com";
        String idToken = "valid-google-token";

        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn(googleEmail);
        when(payload.get("name")).thenReturn("Updated Name");
        when(payload.get("picture")).thenReturn("https://example.com/new-pic.jpg");
        when(googleOAuthProvider.verifyToken(idToken)).thenReturn(payload);

        User existingUser = new User();
        existingUser.setEmail(googleEmail);
        existingUser.setFullName("Old Name");
        existingUser.setAvatarUrl("https://example.com/old-pic.jpg");
        when(userRepository.findByEmail(googleEmail)).thenReturn(Optional.of(existingUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh_token");

        TokenRes tokens = authService.loginWithGoogle(idToken);

        assertNotNull(tokens);
        assertNotNull(tokens.accessToken());
        assertEquals("Updated Name", existingUser.getFullName());
        assertEquals("https://example.com/new-pic.jpg", existingUser.getAvatarUrl());
        verify(userRepository).save(existingUser);
    }

    @Test
    void testLoginWithGoogle_ShouldThrowOnInvalidToken() throws GeneralSecurityException, IOException {
        String invalidToken = "invalid-token";
        when(googleOAuthProvider.verifyToken(invalidToken))
            .thenThrow(new RuntimeException("Invalid Google ID token"));

        assertThrows(RuntimeException.class, () -> authService.loginWithGoogle(invalidToken));

        // Verify no user was created or modified
        verify(userRepository, never()).save(any(User.class));
    }
}
