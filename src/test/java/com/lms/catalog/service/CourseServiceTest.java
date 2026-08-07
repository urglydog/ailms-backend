package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.dto.CourseDto.*;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra BR-COURSE-01 (điều kiện gửi duyệt), BR-COURSE-03 (xoá mềm/cứng),
 * BR-COURSE-04 (giới hạn 5 lần gửi lại) và BR-ROLE-01 (ownership).
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private CourseRepository courseRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CourseService courseService;

    private Course course;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(OWNER_EMAIL);
        instructor.setFullName("Giảng viên A");

        Category category = new Category();
        category.setId(2L);
        category.setName("Tiếng Anh");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa học mẫu");
        course.setDescription("Mô tả đầy đủ");
        course.setThumbnailUrl("https://example.com/thumb.png");
        course.setInstructor(instructor);
        course.setCategory(category);
        course.setStatus(CourseStatus.DRAFT);
        course.setResubmitCount(0);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chapterRepository.findByCourseIdOrderByDisplayOrderAsc(anyLong())).thenReturn(List.of());
    }

    @Test
    void submitForReview_blocksWhenMissingChaptersAndLessons() {
        when(chapterRepository.countByCourseId(10L)).thenReturn(0L);
        lenient().when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(0L);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void submitForReview_fromDraft_movesToPendingWithoutTouchingResubmitCount() {
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(3L);

        DetailRes result = courseService.submitForReview(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PENDING);
        assertThat(result.resubmitCount()).isZero();
    }

    @Test
    void submitForReview_fromRejected_incrementsResubmitCount() {
        course.setStatus(CourseStatus.REJECTED);
        course.setResubmitCount(3);
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(3L);

        DetailRes result = courseService.submitForReview(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PENDING);
        assertThat(result.resubmitCount()).isEqualTo(4);
    }

    @Test
    void submitForReview_fromRejected_blocksAtFifthResubmit() {
        course.setStatus(CourseStatus.REJECTED);
        course.setResubmitCount(5);
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(3L);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("5");

        verify(courseRepository, never()).save(any());
    }

    @Test
    void submitForReview_fromPublished_isRejectedAsInvalidTransition() {
        course.setStatus(CourseStatus.PUBLISHED);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getMineDetail_throwsWhenCallerIsNotOwner() {
        assertThatThrownBy(() -> courseService.getMineDetail("khac@lms.local", 10L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void delete_hardDeletesDraftCourseWithoutEnrollments() {
        when(enrollmentRepository.existsByCourseId(10L)).thenReturn(false);

        courseService.delete(OWNER_EMAIL, 10L);

        verify(courseRepository).delete(course);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void delete_archivesDraftCourseWithEnrollments() {
        when(enrollmentRepository.existsByCourseId(10L)).thenReturn(true);

        courseService.delete(OWNER_EMAIL, 10L);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        verify(courseRepository).save(course);
        verify(courseRepository, never()).delete(any(Course.class));
    }

    @Test
    void delete_archivesPublishedCourseRegardlessOfEnrollments() {
        course.setStatus(CourseStatus.PUBLISHED);

        courseService.delete(OWNER_EMAIL, 10L);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        verify(courseRepository, never()).delete(any(Course.class));
    }

    @Test
    void reject_onlyAllowedFromPending() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> courseService.reject(10L, new RejectReq("Nội dung chưa đạt chất lượng tối thiểu")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reject_fromPending_setsRejectedWithReason() {
        course.setStatus(CourseStatus.PENDING);
        String reason = "Nội dung chưa đạt chất lượng tối thiểu";

        DetailRes result = courseService.reject(10L, new RejectReq(reason));

        assertThat(result.status()).isEqualTo(CourseStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo(reason);
    }

    @Test
    void approve_onlyAllowedFromPending() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> courseService.approve(10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approve_fromPending_movesToPublished() {
        course.setStatus(CourseStatus.PENDING);

        DetailRes result = courseService.approve(10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PUBLISHED);
    }
}
