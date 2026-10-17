package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.service.CourseAccessService;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.dto.EnrollmentDto;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.payment.repository.CartItemRepository;
import java.math.BigDecimal;
import java.util.List;
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
    @Mock private CertificateRepository certificateRepository;

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

    /** "Học tiếp": getMyEnrollments phải trả đúng bài đang học dở và vị trí đã lưu, không phải luôn bài đầu. */
    @Test
    void getMyEnrollments_withUnfinishedLesson_returnsResumeLessonAndPosition() {
        Enrollment enrollment = enrolledInCourseWithLessons();
        Lesson lesson12 = lessonWithId(12L);
        LessonProgress progress = new LessonProgress();
        progress.setLesson(lesson12);
        progress.setLastPositionSec(245);
        progress.setIsCompleted(false);
        when(enrollmentRepository.findByUser_Email(EMAIL)).thenReturn(List.of(enrollment));
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(10L))
                .thenReturn(List.of(lessonWithId(11L), lesson12, lessonWithId(13L)));
        when(lessonProgressRepository.findFirstByUser_IdAndLesson_Chapter_Course_IdOrderByUpdatedAtDesc(1L, 10L))
                .thenReturn(Optional.of(progress));

        EnrollmentDto.Res res = enrollmentService.getMyEnrollments(EMAIL).get(0);

        assertThat(res.resumeLessonId()).isEqualTo(12L);
        assertThat(res.resumePositionSec()).isEqualTo(245);
    }

    @Test
    void getMyEnrollments_withoutProgress_resumesAtFirstLessonFromStart() {
        Enrollment enrollment = enrolledInCourseWithLessons();
        when(enrollmentRepository.findByUser_Email(EMAIL)).thenReturn(List.of(enrollment));
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(10L))
                .thenReturn(List.of(lessonWithId(11L), lessonWithId(12L)));

        EnrollmentDto.Res res = enrollmentService.getMyEnrollments(EMAIL).get(0);

        assertThat(res.resumeLessonId()).isEqualTo(11L);
        assertThat(res.resumePositionSec()).isZero();
    }

    @Test
    void getMyEnrollments_courseWithoutLessons_hasNoResumeLesson() {
        Enrollment enrollment = enrolledInCourseWithLessons();
        when(enrollmentRepository.findByUser_Email(EMAIL)).thenReturn(List.of(enrollment));
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(10L))
                .thenReturn(List.of());

        EnrollmentDto.Res res = enrollmentService.getMyEnrollments(EMAIL).get(0);

        assertThat(res.resumeLessonId()).isNull();
        assertThat(res.resumePositionSec()).isZero();
    }

    private Enrollment enrolledInCourseWithLessons() {
        Category category = new Category();
        category.setName("Lập trình");
        User instructor = new User();
        instructor.setFullName("Trần Thanh Hà");
        freeCourse.setTitle("Unity cơ bản");
        freeCourse.setSlug("unity-co-ban");
        freeCourse.setCategory(category);
        freeCourse.setInstructor(instructor);
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(student);
        enrollment.setCourse(freeCourse);
        enrollment.setProgressPct(BigDecimal.valueOf(60));
        return enrollment;
    }

    private static Lesson lessonWithId(Long id) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        return lesson;
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
