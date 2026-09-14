package com.lms.auth.service;

import com.lms.auth.dto.UserDto.PublicProfileRes;
import com.lms.auth.dto.UserDto.UpdateMyProfileReq;
import com.lms.auth.dto.UserDto.UpdatePrivacyReq;
import com.lms.auth.dto.UserDto.UserRes;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.Role;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private StorageService storageService;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private CourseReviewRepository courseReviewRepository;

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

    /**
     * Unit tests for UserService - UC06: Update My Profile
     * Tests profile update for authenticated users
     */
    @Test
    void testUpdateMyProfile_ShouldUpdateAllProvidedFields() {
        String email = "student@lms.local";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFullName("Old Name");
        user.setAvatarUrl("https://old-avatar.url");
        user.setPreferredLanguage("en");
        user.setRole(Role.STUDENT);
        user.setAuthProvider("LOCAL");
        user.setIsActive(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateMyProfileReq req = new UpdateMyProfileReq(
            "Nguyễn Văn A",
            "https://new-avatar.url",
            "vi"
        );
        UserRes result = userService.updateMyProfile(email, req);

        assertEquals("Nguyễn Văn A", result.fullName());
        assertEquals("https://new-avatar.url", result.avatarUrl());
        assertEquals("vi", result.preferredLanguage());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUpdateMyProfile_ShouldUpdatePartialFields() {
        String email = "student@lms.local";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFullName("Old Name");
        user.setAvatarUrl("https://old-avatar.url");
        user.setPreferredLanguage("en");
        user.setRole(Role.STUDENT);
        user.setAuthProvider("LOCAL");
        user.setIsActive(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateMyProfileReq req = new UpdateMyProfileReq("New Name", null, null);
        UserRes result = userService.updateMyProfile(email, req);

        assertEquals("New Name", result.fullName());
        assertEquals("https://old-avatar.url", result.avatarUrl());
        assertEquals("en", result.preferredLanguage());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUpdateMyProfile_ShouldNotUpdateBlankFields() {
        String email = "student@lms.local";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFullName("Original Name");
        user.setAvatarUrl("https://original-avatar.url");
        user.setPreferredLanguage("en");
        user.setRole(Role.STUDENT);
        user.setAuthProvider("LOCAL");
        user.setIsActive(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateMyProfileReq req = new UpdateMyProfileReq("  ", "", null);
        UserRes result = userService.updateMyProfile(email, req);

        assertEquals("Original Name", result.fullName());
        assertEquals("https://original-avatar.url", result.avatarUrl());
        assertEquals("en", result.preferredLanguage());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUpdateMyProfile_ShouldThrowIfUserNotFound() {
        when(userRepository.findByEmail("nonexistent@lms.local"))
            .thenReturn(Optional.empty());

        UpdateMyProfileReq req = new UpdateMyProfileReq("Name", "url", "en");
        assertThrows(RuntimeException.class, () ->
            userService.updateMyProfile("nonexistent@lms.local", req)
        );

        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * "View public profile" (14/09/2026, mở rộng ngoài đặc tả gốc) — 2 công tắc riêng cho
     * khóa học đã học / wishlist.
     */
    @Test
    void testUpdatePrivacy_ShouldUpdateBothFlags() {
        String email = "student@lms.local";
        User user = new User();
        user.setEmail(email);
        user.setCoursesPublic(true);
        user.setWishlistPublic(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserRes result = userService.updatePrivacy(email, new UpdatePrivacyReq(false, true));

        assertFalse(result.coursesPublic());
        assertTrue(result.wishlistPublic());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUploadAvatar_validImage_updatesAvatarUrl() {
        String email = "student@lms.local";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        });

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(storageService.upload(anyString(), any(), anyLong(), anyString()))
                .thenReturn("https://b2.example.com/avatars/1/abc.png");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserRes result = userService.uploadAvatar(email, file);

        assertEquals("https://b2.example.com/avatars/1/abc.png", result.avatarUrl());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testUploadAvatar_emptyFile_throws() {
        String email = "student@lms.local";
        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThrows(RuntimeException.class, () -> userService.uploadAvatar(email, empty));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetPublicProfile_bothPublic_returnsCoursesAndWishlist() {
        User user = new User();
        user.setId(5L);
        user.setFullName("Nguyen Van A");
        user.setRole(Role.STUDENT);
        user.setCoursesPublic(true);
        user.setWishlistPublic(true);

        Course course = new Course();
        course.setId(10L);
        course.setTitle("Unity co ban");
        course.setSlug("unity-co-ban");

        Enrollment enrollment = new Enrollment();
        enrollment.setCourse(course);

        WishlistItem wishlistItem = new WishlistItem();
        wishlistItem.setCourse(course);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(enrollmentRepository.findByUser_IdOrderByCreatedAtDesc(5L)).thenReturn(List.of(enrollment));
        when(wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(5L)).thenReturn(List.of(wishlistItem));

        PublicProfileRes result = userService.getPublicProfile(5L);

        assertNotNull(result.courses());
        assertEquals(1, result.courses().size());
        assertNotNull(result.wishlist());
        assertEquals(1, result.wishlist().size());
    }

    @Test
    void testGetPublicProfile_bothPrivate_returnsNullLists() {
        User user = new User();
        user.setId(5L);
        user.setFullName("Nguyen Van A");
        user.setRole(Role.STUDENT);
        user.setCoursesPublic(false);
        user.setWishlistPublic(false);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        PublicProfileRes result = userService.getPublicProfile(5L);

        assertNull(result.courses());
        assertNull(result.wishlist());
        verify(enrollmentRepository, never()).findByUser_IdOrderByCreatedAtDesc(anyLong());
        verify(wishlistItemRepository, never()).findByUser_IdOrderByCreatedAtDesc(anyLong());
    }
}
