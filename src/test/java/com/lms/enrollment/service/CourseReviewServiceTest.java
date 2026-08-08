package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.enrollment.dto.CourseReviewDto.*;
import com.lms.enrollment.entity.CourseReview;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra BR-ENROLL-01 (phải sở hữu mới đánh giá được), UNIQUE 1 lần/khóa, và tính lại avgRating. */
@ExtendWith(MockitoExtension.class)
class CourseReviewServiceTest {

    private static final String STUDENT_EMAIL = "student1@lms.local";

    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CourseReviewService courseReviewService;

    private User student;
    private Course course;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setEmail(STUDENT_EMAIL);
        student.setFullName("Nguyen Van A");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khoa hoc test");
        course.setAvgRating(BigDecimal.ZERO);

        lenient().when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(courseReviewRepository.save(any(CourseReview.class))).thenAnswer(inv -> {
            CourseReview review = inv.getArgument(0);
            review.setId(100L);
            return review;
        });
    }

    @Test
    void create_throwsWhenStudentNotEnrolled() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> courseReviewService.create(STUDENT_EMAIL, 10L, new CreateReq(5, "Hay")))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(courseReviewRepository, never()).save(any());
    }

    @Test
    void create_throwsWhenAlreadyReviewed() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);
        when(courseReviewRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> courseReviewService.create(STUDENT_EMAIL, 10L, new CreateReq(5, "Hay")))
                .isInstanceOf(ConflictException.class);

        verify(courseReviewRepository, never()).save(any());
    }

    @Test
    void create_succeedsAndRecalculatesAvgRating() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);
        when(courseReviewRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(courseReviewRepository.findAverageRatingByCourseId(10L)).thenReturn(4.5);

        Res result = courseReviewService.create(STUDENT_EMAIL, 10L, new CreateReq(5, "Rat hay"));

        assertThat(result.rating()).isEqualTo(5);
        assertThat(course.getAvgRating()).isEqualByComparingTo("4.50");
        verify(courseRepository).save(course);
    }

    @Test
    void hide_setsIsHiddenTrueAndRecalculatesAvgRating() {
        CourseReview review = new CourseReview();
        review.setId(200L);
        review.setUser(student);
        review.setCourse(course);
        review.setRating(2);
        review.setIsHidden(false);
        when(courseReviewRepository.findById(200L)).thenReturn(Optional.of(review));
        when(courseReviewRepository.findAverageRatingByCourseId(10L)).thenReturn(0.0);

        Res result = courseReviewService.hide(200L);

        assertThat(result.isHidden()).isTrue();
        ArgumentCaptor<CourseReview> captor = ArgumentCaptor.forClass(CourseReview.class);
        verify(courseReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getIsHidden()).isTrue();
    }

    @Test
    void unhide_setsIsHiddenFalse() {
        CourseReview review = new CourseReview();
        review.setId(200L);
        review.setUser(student);
        review.setCourse(course);
        review.setRating(4);
        review.setIsHidden(true);
        when(courseReviewRepository.findById(200L)).thenReturn(Optional.of(review));
        when(courseReviewRepository.findAverageRatingByCourseId(10L)).thenReturn(4.0);

        Res result = courseReviewService.unhide(200L);

        assertThat(result.isHidden()).isFalse();
    }
}
