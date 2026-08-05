package com.lms.auth.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
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
}
