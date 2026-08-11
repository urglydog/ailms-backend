package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.enrollment.dto.LessonProgressDto.RecordReq;
import com.lms.enrollment.dto.LessonProgressDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-PROGRESS-01 (một chiều, ngưỡng 90%), BR-PROGRESS-02 (% = COMPLETED/READY), completedAt chỉ set 1 lần. */
@ExtendWith(MockitoExtension.class)
class LessonProgressServiceTest {

    private static final String EMAIL = "student1@lms.local";

    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentSecurity enrollmentSecurity;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    private LessonProgressService service;

    private User user;
    private Lesson lesson;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new LessonProgressService(
                lessonRepository, userRepository, enrollmentSecurity, lessonProgressRepository, enrollmentRepository);
        ReflectionTestUtils.setField(service, "completeThresholdPercent", 90);

        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        course = new Course();
        course.setId(10L);

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(21L);
        lesson.setChapter(chapter);
        lesson.setDurationSec(1000);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(lessonRepository.findById(21L)).thenReturn(Optional.of(lesson));
        lenient().when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, false)).thenReturn(true);
        lenient().when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 21L)).thenReturn(Optional.empty());
        lenient().when(lessonProgressRepository.save(org.mockito.ArgumentMatchers.any(LessonProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void chuaGhiDanh_biChan() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, false)).thenReturn(false);

        assertThatThrownBy(() -> service.recordProgress(EMAIL, 21L, new RecordReq(100, 100)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void watchedSec_khongGiam_khiGuiSoNhoHon() {
        LessonProgress existing = new LessonProgress();
        existing.setUser(user);
        existing.setLesson(lesson);
        existing.setWatchedSec(500);
        existing.setLastPositionSec(500);
        when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 21L)).thenReturn(Optional.of(existing));

        Res res = service.recordProgress(EMAIL, 21L, new RecordReq(300, 300));

        // watchedSec giữ max(500, 300) = 500 — không tính lùi dù request gửi số nhỏ hơn (mạng lỗi/gửi lại).
        assertThat(res.watchedSec()).isEqualTo(500);
        // lastPositionSec luôn ghi đè theo giá trị mới nhất — tua lùi xem lại là hợp lệ.
        assertThat(res.lastPositionSec()).isEqualTo(300);
    }

    @Test
    void chuaDatNguong90Phantram_chuaHoanThanh() {
        Res res = service.recordProgress(EMAIL, 21L, new RecordReq(899, 899));

        assertThat(res.isCompleted()).isFalse();
    }

    @Test
    void dat90Phantram_hoanThanh() {
        Res res = service.recordProgress(EMAIL, 21L, new RecordReq(900, 900));

        assertThat(res.isCompleted()).isTrue();
    }

    @Test
    void daHoanThanh_xemLaiTuDauKhongBiHuy() {
        LessonProgress existing = new LessonProgress();
        existing.setUser(user);
        existing.setLesson(lesson);
        existing.setWatchedSec(950);
        existing.setLastPositionSec(950);
        existing.setIsCompleted(true);
        when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 21L)).thenReturn(Optional.of(existing));

        Res res = service.recordProgress(EMAIL, 21L, new RecordReq(50, 50));

        // Tua về đầu xem lại (lastPositionSec=50, watchedSec mới thấp hơn watchedSec cũ) —
        // isCompleted đã true thì không bao giờ quay lại false (một chiều, BR-PROGRESS-01).
        assertThat(res.isCompleted()).isTrue();
    }

    @Test
    void khongCoEnrollment_khongCapNhatProgressPct() {
        when(enrollmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.empty());

        service.recordProgress(EMAIL, 21L, new RecordReq(100, 100));

        verify(enrollmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void coEnrollment_tinhDungPhanTramTheoBaiReady() {
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setProgressPct(BigDecimal.ZERO);
        when(enrollmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrollment));
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(4L);
        when(lessonProgressRepository.countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(1L, 10L))
                .thenReturn(1L);

        service.recordProgress(EMAIL, 21L, new RecordReq(100, 100));

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getProgressPct()).isEqualByComparingTo("25.00");
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void hoanThanhTatCaBai_setCompletedAt() {
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setProgressPct(BigDecimal.valueOf(75));
        when(enrollmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrollment));
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(4L);
        when(lessonProgressRepository.countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(1L, 10L))
                .thenReturn(4L);

        service.recordProgress(EMAIL, 21L, new RecordReq(900, 900));

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getProgressPct()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void daCoCompletedAt_khongGhiDeLai() {
        java.time.LocalDateTime firstCompletedAt = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setProgressPct(BigDecimal.valueOf(100));
        enrollment.setCompletedAt(firstCompletedAt);
        when(enrollmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrollment));
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(4L);
        when(lessonProgressRepository.countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(1L, 10L))
                .thenReturn(4L);

        service.recordProgress(EMAIL, 21L, new RecordReq(900, 900));

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isEqualTo(firstCompletedAt);
    }
}
