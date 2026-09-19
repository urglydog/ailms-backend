package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseInviteRepository;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — logic dùng chung bởi ghi danh miễn phí/
 * giỏ hàng/mua trực tiếp. */
@ExtendWith(MockitoExtension.class)
class CourseAccessServiceTest {

    private static final String INSTRUCTOR_EMAIL = "giangvien@lms.local";
    private static final String INVITED_EMAIL = "duoc-moi@lms.local";
    private static final String STRANGER_EMAIL = "nguoila@lms.local";

    @Mock private CourseInviteRepository courseInviteRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CourseAccessService courseAccessService;

    private Course course;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail(INSTRUCTOR_EMAIL);

        course = new Course();
        course.setId(10L);
        course.setInstructor(instructor);
    }

    @Test
    void isInvited_owningInstructor_true() {
        assertThat(courseAccessService.isInvited(course, INSTRUCTOR_EMAIL)).isTrue();
    }

    @Test
    void isInvited_emailOnInviteList_true() {
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, INVITED_EMAIL)).thenReturn(true);

        assertThat(courseAccessService.isInvited(course, INVITED_EMAIL)).isTrue();
    }

    @Test
    void isInvited_strangerAndNullEmail_false() {
        lenient().when(courseInviteRepository.existsByCourse_IdAndEmail(10L, STRANGER_EMAIL)).thenReturn(false);

        assertThat(courseAccessService.isInvited(course, STRANGER_EMAIL)).isFalse();
        assertThat(courseAccessService.isInvited(course, null)).isFalse();
    }

    @Test
    void verifyCanEnroll_public_neverThrows() {
        course.setVisibility(CourseVisibility.PUBLIC);

        assertThatCode(() -> courseAccessService.verifyCanEnroll(course, STRANGER_EMAIL, null)).doesNotThrowAnyException();
    }

    @Test
    void verifyCanEnroll_privateInvite_strangerThrows() {
        course.setVisibility(CourseVisibility.PRIVATE_INVITE);
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, STRANGER_EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> courseAccessService.verifyCanEnroll(course, STRANGER_EMAIL, null))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void verifyCanEnroll_privateInvite_invitedPasses() {
        course.setVisibility(CourseVisibility.PRIVATE_INVITE);
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, INVITED_EMAIL)).thenReturn(true);

        assertThatCode(() -> courseAccessService.verifyCanEnroll(course, INVITED_EMAIL, null)).doesNotThrowAnyException();
    }

    @Test
    void verifyCanEnroll_privatePassword_wrongPasswordThrows() {
        course.setVisibility(CourseVisibility.PRIVATE_PASSWORD);
        course.setEnrollPasswordHash("hashed");
        when(passwordEncoder.matches("sai-mat-khau", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> courseAccessService.verifyCanEnroll(course, STRANGER_EMAIL, "sai-mat-khau"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void verifyCanEnroll_privatePassword_nullPasswordThrows() {
        course.setVisibility(CourseVisibility.PRIVATE_PASSWORD);
        course.setEnrollPasswordHash("hashed");

        assertThatThrownBy(() -> courseAccessService.verifyCanEnroll(course, STRANGER_EMAIL, null))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void verifyCanEnroll_privatePassword_correctPasswordPasses() {
        course.setVisibility(CourseVisibility.PRIVATE_PASSWORD);
        course.setEnrollPasswordHash("hashed");
        when(passwordEncoder.matches("dung-mat-khau", "hashed")).thenReturn(true);

        assertThatCode(() -> courseAccessService.verifyCanEnroll(course, STRANGER_EMAIL, "dung-mat-khau"))
                .doesNotThrowAnyException();
    }

    @Test
    void verifyCanAddToCart_privatePassword_alwaysThrows_evenWithNoPasswordNeeded() {
        course.setVisibility(CourseVisibility.PRIVATE_PASSWORD);
        course.setEnrollPasswordHash("hashed");

        assertThatThrownBy(() -> courseAccessService.verifyCanAddToCart(course, STRANGER_EMAIL))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void verifyCanAddToCart_privateInvite_behavesSameAsVerifyCanEnroll() {
        course.setVisibility(CourseVisibility.PRIVATE_INVITE);
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, INVITED_EMAIL)).thenReturn(true);

        assertThatCode(() -> courseAccessService.verifyCanAddToCart(course, INVITED_EMAIL)).doesNotThrowAnyException();
    }
}
