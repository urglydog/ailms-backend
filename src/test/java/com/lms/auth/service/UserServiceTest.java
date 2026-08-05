package com.lms.auth.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService - UC05: Change Password
 * Tests password verification and update for authenticated users
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void testChangePassword_ShouldVerifyCurrentAndUpdatePassword() {
        String email = "student@lms.local";
        String currentPassword = "OldPassword123!";
        String newPassword = "NewPassword456!";
        String hashedOldPassword = "$2a$10$hashedOldPassword";
        String hashedNewPassword = "$2a$10$hashedNewPassword";

        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash(hashedOldPassword);
        user.setFullName("Test Student");
        user.setRole(Role.STUDENT);
        user.setAuthProvider("LOCAL");
        user.setIsActive(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, hashedOldPassword)).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(hashedNewPassword);
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.changePassword(email, currentPassword, newPassword);

        verify(passwordEncoder, times(1)).matches(currentPassword, hashedOldPassword);
        verify(passwordEncoder, times(1)).encode(newPassword);
        verify(userRepository, times(1)).save(user);
        assertEquals(hashedNewPassword, user.getPasswordHash());
    }

    @Test
    void testChangePassword_ShouldThrowOnWrongCurrentPassword() {
        String email = "student@lms.local";
        String correctPassword = "OldPassword123!";
        String wrongPassword = "WrongPassword!";
        String newPassword = "NewPassword456!";
        String hashedPassword = "$2a$10$hashedPassword";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(hashedPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(wrongPassword, hashedPassword)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
            userService.changePassword(email, wrongPassword, newPassword)
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testChangePassword_ShouldThrowIfUserNotFound() {
        when(userRepository.findByEmail("nonexistent@lms.local"))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
            userService.changePassword("nonexistent@lms.local", "old", "new")
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testChangePassword_ShouldThrowIfPasswordHashIsNull() {
        String email = "google-user@gmail.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null); // Google OAuth user

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () ->
            userService.changePassword(email, "anything", "NewPassword!")
        );

        verify(userRepository, never()).save(any(User.class));
    }
}
