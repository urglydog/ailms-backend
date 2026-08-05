package com.lms.auth.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.times;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    @Test
    void testForgotPassword_ShouldSendOtpAndStoreInRedis() {
        String email = "student@lms.local";
        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:forgot:resend:" + email)).thenReturn(null);

        authService.forgotPassword(email);

        verify(emailService, times(1)).sendOtpEmail(email, anyString());
        verify(redisTemplate, times(1)).opsForValue();
    }
}
