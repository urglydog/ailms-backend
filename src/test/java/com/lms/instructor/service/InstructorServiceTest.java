package com.lms.instructor.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.instructor.dto.InstructorVerificationDto.Res;
import com.lms.instructor.dto.InstructorVerificationDto.StatusRes;
import com.lms.instructor.entity.InstructorVerification;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Trở thành Giảng viên" kiểu Udemy (15/09/2026, thiết kế lại — thay UC41 cũ).
 *
 * <p>(19/09/2026) — khôi phục lại sau khi bị mất khỏi nhánh {@code feat/dubbing} trong lúc merge
 * PR #132.
 */
@ExtendWith(MockitoExtension.class)
class InstructorServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private UserRepository userRepository;
    @Mock private InstructorVerificationRepository verificationRepository;

    @InjectMocks
    private InstructorService instructorService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setRole(Role.STUDENT);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(verificationRepository.save(any(InstructorVerification.class))).thenAnswer(inv -> {
            InstructorVerification v = inv.getArgument(0);
            v.setId(500L);
            return v;
        });
    }

    @Test
    void becomeInstructor_fromStudent_upgradesRoleImmediately() {
        instructorService.becomeInstructor(EMAIL);

        assertThat(user.getRole()).isEqualTo(Role.INSTRUCTOR);
        verify(userRepository).save(user);
    }

    @Test
    void becomeInstructor_alreadyInstructor_throws() {
        user.setRole(Role.INSTRUCTOR);

        assertThatThrownBy(() -> instructorService.becomeInstructor(EMAIL))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void becomeInstructor_admin_throws() {
        user.setRole(Role.ADMIN);

        assertThatThrownBy(() -> instructorService.becomeInstructor(EMAIL))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getMyStatus_noVerificationYet_returnsFalse() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        StatusRes result = instructorService.getMyStatus(EMAIL);

        assertThat(result.verified()).isFalse();
    }

    @Test
    void getMyStatus_alreadyVerified_returnsTrue() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(true);

        StatusRes result = instructorService.getMyStatus(EMAIL);

        assertThat(result.verified()).isTrue();
    }

    @Test
    void submit_validData_savesAndReturnsRes() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        Res result = instructorService.submit(EMAIL, "001234567890", "123 Đường ABC, Q1, TP.HCM", true);

        assertThat(result.idNumber()).isEqualTo("001234567890");
        assertThat(result.contentOwnershipConfirmed()).isTrue();
        assertThat(result.verifiedAt()).isNotNull();
        verify(verificationRepository).save(any(InstructorVerification.class));
    }

    @Test
    void submit_alreadyVerified_throws() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> instructorService.submit(EMAIL, "001234567890", "123 ABC", true))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submit_withoutContentOwnershipConfirmed_throws() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        assertThatThrownBy(() -> instructorService.submit(EMAIL, "001234567890", "123 ABC", false))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void submit_blankIdNumber_throws() {
        when(verificationRepository.existsByUser_Id(1L)).thenReturn(false);

        assertThatThrownBy(() -> instructorService.submit(EMAIL, "  ", "123 ABC", true))
                .isInstanceOf(InvalidRequestException.class);
    }
}
