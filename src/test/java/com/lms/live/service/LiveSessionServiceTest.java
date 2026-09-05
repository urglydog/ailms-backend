package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.live.dto.LiveSessionDto.CreateReq;
import com.lms.live.dto.LiveSessionDto.Res;
import com.lms.live.dto.LiveSessionDto.StartRes;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.RoomServiceClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import retrofit2.Call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC50 (F11.1) — vòng đời SCHEDULED → LIVE → ENDED, ownership BR-ROLE-01, BR-LIVE-04. */
@ExtendWith(MockitoExtension.class)
class LiveSessionServiceTest {

    private static final String INSTRUCTOR_EMAIL = "instructor@lms.local";
    private static final String OTHER_INSTRUCTOR_EMAIL = "other@lms.local";
    private static final Long COURSE_ID = 10L;
    private static final Long SESSION_ID = 20L;

    @Mock private LiveSessionRepository liveSessionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private LiveKitConfig liveKitConfig;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RoomServiceClient roomServiceClient;
    @Mock private Call<Void> deleteRoomCall;
    @Mock private StorageService storageService;

    private LiveSessionService liveSessionService;

    private User instructor;
    private User otherInstructor;
    private Course course;

    @BeforeEach
    void setUp() throws Exception {
        liveSessionService = new LiveSessionService(liveSessionRepository, courseRepository, userRepository,
                liveKitConfig, eventPublisher, roomServiceClient, storageService);
        lenient().when(roomServiceClient.deleteRoom(anyString())).thenReturn(deleteRoomCall);
        lenient().when(deleteRoomCall.execute()).thenReturn(null);

        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setFullName("Cô Lan");
        instructor.setRole(Role.INSTRUCTOR);
        instructor.setPreferredLanguage("vi-VN");

        otherInstructor = new User();
        otherInstructor.setId(2L);
        otherInstructor.setEmail(OTHER_INSTRUCTOR_EMAIL);
        otherInstructor.setRole(Role.INSTRUCTOR);

        course = new Course();
        course.setId(COURSE_ID);
        course.setTitle("Java co ban");
        course.setInstructor(instructor);

        lenient().when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByEmail(OTHER_INSTRUCTOR_EMAIL)).thenReturn(Optional.of(otherInstructor));
        lenient().when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        lenient().when(liveKitConfig.getApiKey()).thenReturn("test-api-key");
        lenient().when(liveKitConfig.getApiSecret()).thenReturn("test-api-secret-32-chars-minimum");
        lenient().when(liveKitConfig.getServerUrl()).thenReturn("wss://test.livekit.cloud");
        lenient().when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LiveSession scheduledSession() {
        LiveSession session = new LiveSession();
        session.setId(SESSION_ID);
        session.setInstructor(instructor);
        session.setCourse(course);
        session.setTitle("Buoi live so 1");
        session.setVisibility(LiveVisibility.PUBLIC);
        session.setStatus(LiveSessionStatus.SCHEDULED);
        session.setSourceLanguage("vi-VN");
        session.setRoomName("live-abc123");
        return session;
    }

    @Test
    void create_khongTruyenSourceLanguage_layTheoPreferredLanguageCuaGiangVien() {
        CreateReq req = new CreateReq(COURSE_ID, "Buoi live so 1", LiveVisibility.PUBLIC, null, null);

        Res res = liveSessionService.create(INSTRUCTOR_EMAIL, req);

        assertThat(res.sourceLanguage()).isEqualTo("vi-VN");
        assertThat(res.status()).isEqualTo(LiveSessionStatus.SCHEDULED);
        assertThat(res.roomName()).isNotBlank();
    }

    @Test
    void create_giangVienKhongSoHuuKhoaHoc_bBiChan() {
        CreateReq req = new CreateReq(COURSE_ID, "Buoi live so 1", LiveVisibility.PUBLIC, null, null);

        assertThatThrownBy(() -> liveSessionService.create(OTHER_INSTRUCTOR_EMAIL, req))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void start_dangSCHEDULED_chuyenLiveVaSinhToken() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        StartRes res = liveSessionService.start(INSTRUCTOR_EMAIL, SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.identity()).isEqualTo("instructor-1");
        assertThat(res.roomName()).isEqualTo(session.getRoomName());
        assertThat(res.serverUrl()).isEqualTo("wss://test.livekit.cloud");
    }

    @Test
    void start_khongPhaiChuSoHuu_bBiChan() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> liveSessionService.start(OTHER_INSTRUCTOR_EMAIL, SESSION_ID))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void start_phienDangLiveRoi_choPhepVaoLaiKhongDoiStartedAt() {
        LiveSession session = scheduledSession();
        session.setStatus(LiveSessionStatus.LIVE);
        session.setStartedAt(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        session.setInstructorDisconnectedAt(java.time.LocalDateTime.now());
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        StartRes res = liveSessionService.start(INSTRUCTOR_EMAIL, SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(session.getStartedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        assertThat(session.getInstructorDisconnectedAt()).isNull();
        assertThat(res.accessToken()).isNotBlank();
    }

    @Test
    void start_phienDaENDED_bBiChanKhongChoBatDauLai() {
        LiveSession session = scheduledSession();
        session.setStatus(LiveSessionStatus.ENDED);
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> liveSessionService.start(INSTRUCTOR_EMAIL, SESSION_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void start_phienKhongTonTai_nemResourceNotFound() {
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> liveSessionService.start(INSTRUCTOR_EMAIL, SESSION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void end_dangLive_chuyenEndedVaPublishEvent() {
        LiveSession session = scheduledSession();
        session.setStatus(LiveSessionStatus.LIVE);
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        Res res = liveSessionService.end(INSTRUCTOR_EMAIL, SESSION_ID);

        assertThat(res.status()).isEqualTo(LiveSessionStatus.ENDED);
        assertThat(session.getEndedAt()).isNotNull();

        ArgumentCaptor<LiveSessionEndedEvent> captor = ArgumentCaptor.forClass(LiveSessionEndedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().liveSessionId()).isEqualTo(SESSION_ID);

        // Chống phí LiveKit oan (BUG THẬT 31/08/2026) — phải chủ động đóng phòng, không chỉ đổi
        // trạng thái DB rồi tin trình duyệt tự rời.
        verify(roomServiceClient).deleteRoom(session.getRoomName());
    }

    @Test
    void end_dangLive_maLiveKitLoiKhiDongPhong_vanKetThucBinhThuong() throws Exception {
        LiveSession session = scheduledSession();
        session.setStatus(LiveSessionStatus.LIVE);
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(deleteRoomCall.execute()).thenThrow(new java.io.IOException("LiveKit khong ket noi duoc"));

        Res res = liveSessionService.end(INSTRUCTOR_EMAIL, SESSION_ID);

        assertThat(res.status()).isEqualTo(LiveSessionStatus.ENDED);
    }

    @Test
    void end_chuaBatDau_bBiChanKhongChoKetThucPhienDangSCHEDULED() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> liveSessionService.end(INSTRUCTOR_EMAIL, SESSION_ID))
                .isInstanceOf(ConflictException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void uploadThumbnail_anhJpegHopLe_thanhCong() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        byte[] jpegMagicBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "thumb.jpg", "image/jpeg", jpegMagicBytes);
        when(storageService.upload(anyString(), any(), anyLong(), eq("image/jpeg")))
                .thenReturn("https://cdn.example.com/live-thumbnails/20/x.jpg");

        Res result = liveSessionService.uploadThumbnail(INSTRUCTOR_EMAIL, SESSION_ID, file);

        assertThat(result.thumbnailUrl()).isEqualTo("https://cdn.example.com/live-thumbnails/20/x.jpg");
    }

    @Test
    void uploadThumbnail_fileGiaMaoAnh_nemInvalidRequest() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        MockMultipartFile file = new MockMultipartFile(
                "file", "thumb.jpg", "image/jpeg", "%PDF-1.4 gia mao anh".getBytes());

        assertThatThrownBy(() -> liveSessionService.uploadThumbnail(INSTRUCTOR_EMAIL, SESSION_ID, file))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void uploadThumbnail_vuotQua5MB_nemBusinessRuleViolation() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        MockMultipartFile file = new MockMultipartFile(
                "file", "thumb.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> liveSessionService.uploadThumbnail(INSTRUCTOR_EMAIL, SESSION_ID, file))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void uploadThumbnail_khongPhaiChuSoHuu_nemAccessDenied() {
        LiveSession session = scheduledSession();
        when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        MockMultipartFile file = new MockMultipartFile("file", "thumb.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> liveSessionService.uploadThumbnail(OTHER_INSTRUCTOR_EMAIL, SESSION_ID, file))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
