package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.live.dto.LiveViewDto.DetailRes;
import com.lms.live.dto.LiveViewDto.SummaryRes;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import com.lms.live.repository.LiveSessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** UC51 (F11.2) — phân quyền xem theo BR-LIVE-01. */
@ExtendWith(MockitoExtension.class)
class LiveViewServiceTest {

    private static final String STUDENT_EMAIL = "student@lms.local";
    private static final String OTHER_STUDENT_EMAIL = "other@lms.local";
    private static final String INSTRUCTOR_EMAIL = "instructor@lms.local";
    private static final String ADMIN_EMAIL = "admin@lms.local";
    private static final Long COURSE_ID = 10L;
    private static final Long SESSION_ID = 20L;

    @Mock private LiveSessionRepository liveSessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private LiveKitConfig liveKitConfig;

    private LiveViewService liveViewService;

    private User student;
    private User otherStudent;
    private User instructor;
    private User admin;
    private Course course;

    @BeforeEach
    void setUp() {
        liveViewService = new LiveViewService(liveSessionRepository, userRepository, enrollmentRepository, liveKitConfig);

        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setFullName("Co Lan");
        instructor.setRole(Role.INSTRUCTOR);

        student = new User();
        student.setId(2L);
        student.setEmail(STUDENT_EMAIL);
        student.setRole(Role.STUDENT);

        otherStudent = new User();
        otherStudent.setId(3L);
        otherStudent.setEmail(OTHER_STUDENT_EMAIL);
        otherStudent.setRole(Role.STUDENT);

        admin = new User();
        admin.setId(4L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        course = new Course();
        course.setId(COURSE_ID);
        course.setTitle("Java co ban");
        course.setInstructor(instructor);

        lenient().when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        lenient().when(userRepository.findByEmail(OTHER_STUDENT_EMAIL)).thenReturn(Optional.of(otherStudent));
        lenient().when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), COURSE_ID)).thenReturn(true);
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(otherStudent.getId(), COURSE_ID)).thenReturn(false);
        lenient().when(liveKitConfig.getApiKey()).thenReturn("test-api-key");
        lenient().when(liveKitConfig.getApiSecret()).thenReturn("test-api-secret-32-chars-minimum");
        lenient().when(liveKitConfig.getServerUrl()).thenReturn("wss://test.livekit.cloud");
    }

    private LiveSession session(LiveVisibility visibility, LiveSessionStatus status) {
        LiveSession s = new LiveSession();
        s.setId(SESSION_ID);
        s.setInstructor(instructor);
        s.setCourse(course);
        s.setTitle("Buoi live");
        s.setVisibility(visibility);
        s.setStatus(status);
        s.setSourceLanguage("vi-VN");
        s.setRoomName("live-abc");
        return s;
    }

    @Test
    void view_phienPUBLIC_guestXemDuoc() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.PUBLIC, LiveSessionStatus.LIVE)));

        DetailRes res = liveViewService.view(null, SESSION_ID);

        assertThat(res.status()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(res.viewerToken()).isNotBlank();
        assertThat(res.serverUrl()).isEqualTo("wss://test.livekit.cloud");
    }

    @Test
    void view_phienCOURSE_ONLY_guestBiChan() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.LIVE)));

        assertThatThrownBy(() -> liveViewService.view(null, SESSION_ID))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void view_phienCOURSE_ONLY_hocVienChuaGhiDanh_bBiChan() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.LIVE)));

        assertThatThrownBy(() -> liveViewService.view(OTHER_STUDENT_EMAIL, SESSION_ID))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void view_phienCOURSE_ONLY_hocVienDaGhiDanh_xemDuoc() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.LIVE)));

        DetailRes res = liveViewService.view(STUDENT_EMAIL, SESSION_ID);

        assertThat(res.viewerToken()).isNotBlank();
    }

    @Test
    void view_phienCOURSE_ONLY_giangVienSoHuu_xemDuoc() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.LIVE)));

        DetailRes res = liveViewService.view(INSTRUCTOR_EMAIL, SESSION_ID);

        assertThat(res.viewerToken()).isNotBlank();
    }

    @Test
    void view_phienCOURSE_ONLY_admin_xemDuoc() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.LIVE)));

        DetailRes res = liveViewService.view(ADMIN_EMAIL, SESSION_ID);

        assertThat(res.viewerToken()).isNotBlank();
    }

    @Test
    void view_phienDangSCHEDULED_khongCoViewerToken() {
        when(liveSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session(LiveVisibility.PUBLIC, LiveSessionStatus.SCHEDULED)));

        DetailRes res = liveViewService.view(null, SESSION_ID);

        assertThat(res.viewerToken()).isNull();
        assertThat(res.serverUrl()).isNull();
        assertThat(res.roomName()).isNull();
    }

    @Test
    void view_phienKhongTonTai_nemResourceNotFound() {
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> liveViewService.view(null, SESSION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForCourse_locBoPhienCOURSE_ONLYVoiGuest_giuLaiPUBLIC() {
        LiveSession publicSession = session(LiveVisibility.PUBLIC, LiveSessionStatus.LIVE);
        LiveSession courseOnlySession = session(LiveVisibility.COURSE_ONLY, LiveSessionStatus.SCHEDULED);
        courseOnlySession.setId(21L);
        when(liveSessionRepository.findByCourse_IdAndStatusIn(COURSE_ID,
                List.of(LiveSessionStatus.SCHEDULED, LiveSessionStatus.LIVE)))
                .thenReturn(List.of(publicSession, courseOnlySession));

        List<SummaryRes> result = liveViewService.listForCourse(null, COURSE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(SESSION_ID);
    }
}
