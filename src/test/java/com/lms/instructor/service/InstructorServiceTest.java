package com.lms.instructor.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.storage.StorageService;
import com.lms.instructor.dto.InstructorVerificationDto.Res;
import com.lms.instructor.dto.InstructorVerificationDto.StatusRes;
import com.lms.instructor.entity.InstructorVerification;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đăng ký Giảng viên kiểu Udemy (15/09/2026) — kiểm tra nâng cấp vai trò NGAY LẬP TỨC (thay
 * UC41 cũ) và BR-VERIFY-01 (xác minh định danh 1 lần/tài khoản).
 */
@ExtendWith(MockitoExtension.class)
class InstructorServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private UserRepository userRepository;
    @Mock private InstructorVerificationRepository verificationRepository;
    @Mock private StorageService storageService;

    @InjectMocks
    private InstructorService instructorService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setRole(Role.STUDENT);
    }

    @Test
    void becomeInstructor_fromStudent_upgradesRoleImmediately() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        instructorService.becomeInstructor(EMAIL);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void becomeInstructor_alreadyInstructor_throws() {
        user.setRole(Role.INSTRUCTOR);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> instructorService.becomeInstructor(EMAIL))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getMyStatus_noVerificationYet_returnsFalse() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        StatusRes result = instructorService.getMyStatus(EMAIL);

        assertThat(result.verified()).isFalse();
    }

    @Test
    void submit_validData_savesAndReturnsRes() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);
        when(storageService.upload(anyString(), any(), anyLong(), eq("image/jpeg")))
                .thenReturn("https://b2.example.com/instructor-verification/1/x.jpg");
        when(verificationRepository.save(any(InstructorVerification.class))).thenAnswer(inv -> {
            InstructorVerification v = inv.getArgument(0);
            v.setId(500L);
            return v;
        });

        byte[] jpegMagicBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "cccd.jpg", "image/jpeg", jpegMagicBytes);

        Res result = instructorService.submit(EMAIL, "001234567890", "123 Đường ABC, Q1, TP.HCM", true, file);

        assertThat(result.idNumber()).isEqualTo("001234567890");
        assertThat(result.idPhotoUrl()).isEqualTo("https://b2.example.com/instructor-verification/1/x.jpg");
        assertThat(result.contentOwnershipConfirmed()).isTrue();
        assertThat(result.verifiedAt()).isNotNull();
    }

    @Test
    void submit_alreadyVerified_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "cccd.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> instructorService.submit(EMAIL, "001", "Địa chỉ", true, file))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void submit_withoutContentOwnershipConfirmed_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "cccd.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> instructorService.submit(EMAIL, "001", "Địa chỉ", false, file))
                .isInstanceOf(InvalidRequestException.class);

        verify(verificationRepository, never()).save(any());
    }
}
