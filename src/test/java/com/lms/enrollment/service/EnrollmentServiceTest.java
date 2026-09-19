package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.service.CourseAccessService;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.payment.repository.CartItemRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-ENROLL-01 (ghi danh miễn phí) + "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026, mở
 * rộng): PRIVATE_INVITE/PRIVATE_PASSWORD được {@link CourseAccessService} chặn TRƯỚC các điều
 * kiện BR-ENROLL-01. */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final String EMAIL = "hocvien@lms.local";

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CourseAccessService courseAccessService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private User student;
    private Course freeCourse;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setEmail(EMAIL);

        freeCourse = new Course();
        freeCourse.setId(10L);
        freeCourse.setStatus(CourseStatus.PUBLISHED);
        freeCourse.setIsFree(true);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(freeCourse));
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
    }

    @Test
    void enrollFreeCourse_publicFreeCourse_savesEnrollment() {
        enrollmentService.enrollFreeCourse(EMAIL, 10L, null);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(student);
        assertThat(captor.getValue().getCourse()).isEqualTo(freeCourse);
        verify(cartItemRepository).deleteByUser_IdAndCourse_Id(1L, 10L);
    }

    @Test
    void enrollFreeCourse_notPublished_throwsBusinessRule() {
        freeCourse.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(EMAIL, 10L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void enrollFreeCourse_notFree_throwsBusinessRule() {
        freeCourse.setIsFree(false);

        assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(EMAIL, 10L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void enrollFreeCourse_alreadyOwned_throwsBusinessRule() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(EMAIL, 10L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /** "Đăng ký (Quyền riêng tư)" — CourseAccessService chặn TRƯỚC khi kiểm status/isFree/đã sở
     * hữu, nên khóa PRIVATE bị chặn dù mọi điều kiện BR-ENROLL-01 khác đều hợp lệ. */
    @Test
    void enrollFreeCourse_courseAccessDenied_neverSavesEnrollment() {
        freeCourse.setVisibility(CourseVisibility.PRIVATE_INVITE);
        doThrow(new AccessDeniedDomainException("Khóa học này chỉ dành cho người được Giảng viên mời."))
                .when(courseAccessService).verifyCanEnroll(freeCourse, EMAIL, null);

        assertThatThrownBy(() -> enrollmentService.enrollFreeCourse(EMAIL, 10L, null))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(enrollmentRepository, never()).save(any());
    }
}
