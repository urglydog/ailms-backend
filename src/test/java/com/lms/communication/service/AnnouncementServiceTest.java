package com.lms.communication.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import com.lms.communication.dto.AnnouncementDto.CreateReq;
import com.lms.communication.dto.AnnouncementDto.Res;
import com.lms.communication.entity.Announcement;
import com.lms.communication.repository.AnnouncementRepository;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Giao tiếp > Thông báo" (19/09/2026) — kiểm tra fan-out Notification tới đúng học viên đã
 * ghi danh, và các kiểm quyền sở hữu khóa học. */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final String EMAIL = "instructor@lms.local";

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AnnouncementService announcementService;

    private User instructor;
    private Course course;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(EMAIL);

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa Java");
        course.setSlug("khoa-java");
        course.setInstructor(instructor);
    }

    @Test
    void create_notifiesEveryEnrolledStudent() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User s1 = studentWithId(2L);
        User s2 = studentWithId(3L);
        when(enrollmentRepository.findByCourseId(10L)).thenReturn(List.of(enrollmentOf(s1), enrollmentOf(s2)));

        Res res = announcementService.create(EMAIL, new CreateReq(10L, "Lịch live mới", "Thứ 7 tuần này"));

        assertThat(res.title()).isEqualTo("Lịch live mới");
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationService, times(2)).notify(userIdCaptor.capture(), eq("ANNOUNCEMENT"), any(), any(), any());
        assertThat(userIdCaptor.getAllValues()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void create_notCourseOwner_throwsAccessDenied() {
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> announcementService.create(EMAIL, new CreateReq(10L, "X", "Y")))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void create_blankTitle_throwsInvalidRequest() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> announcementService.create(EMAIL, new CreateReq(10L, "  ", "Nội dung")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void listForStudent_notEnrolled_throwsAccessDenied() {
        when(enrollmentRepository.existsByUser_EmailAndCourse_Id("hocvien@lms.local", 10L)).thenReturn(false);

        assertThatThrownBy(() -> announcementService.listForStudent("hocvien@lms.local", 10L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void delete_notOwner_throwsAccessDenied() {
        Announcement announcement = new Announcement();
        announcement.setCourse(course);
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        when(announcementRepository.findById(5L)).thenReturn(Optional.of(announcement));

        assertThatThrownBy(() -> announcementService.delete(EMAIL, 5L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    private User studentWithId(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Enrollment enrollmentOf(User student) {
        Enrollment e = new Enrollment();
        e.setUser(student);
        e.setCourse(course);
        return e;
    }
}
