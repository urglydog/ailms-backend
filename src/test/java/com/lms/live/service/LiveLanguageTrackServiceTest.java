package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.live.dto.LiveLanguageTrackDto.ActivateReq;
import com.lms.live.dto.LiveLanguageTrackDto.Res;
import com.lms.live.entity.LiveLanguageTrack;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveTrackStatus;
import com.lms.live.enums.LiveVisibility;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveLanguageTrackRepository;
import com.lms.live.repository.LiveSessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC52 (F11.3) — BR-LIVE-05 (dedupe track theo ngôn ngữ) và BR-LIVE-06 (chỉ ngôn ngữ active). */
@ExtendWith(MockitoExtension.class)
class LiveLanguageTrackServiceTest {

    private static final String STUDENT_EMAIL = "student@lms.local";
    private static final Long SESSION_ID = 20L;
    private static final Long COURSE_ID = 10L;

    @Mock private LiveSessionRepository liveSessionRepository;
    @Mock private LiveLanguageTrackRepository liveLanguageTrackRepository;
    @Mock private VoiceMappingRepository voiceMappingRepository;
    @Mock private LiveViewService liveViewService;
    @Mock private LiveKitConfig liveKitConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    private LiveLanguageTrackService service;

    private User student;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new LiveLanguageTrackService(liveSessionRepository, liveLanguageTrackRepository,
                voiceMappingRepository, liveViewService, liveKitConfig, restTemplate, aiWorkerConfig);
        ReflectionTestUtils.setField(service, "internalApiToken", "dev-internal-token");

        student = new User();
        student.setId(2L);
        student.setEmail(STUDENT_EMAIL);
        student.setRole(Role.STUDENT);

        User instructor = new User();
        instructor.setId(1L);
        instructor.setFullName("Co Lan");

        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructor(instructor);

        session = new LiveSession();
        session.setId(SESSION_ID);
        session.setInstructor(instructor);
        session.setCourse(course);
        session.setVisibility(LiveVisibility.PUBLIC);
        session.setStatus(LiveSessionStatus.LIVE);
        session.setSourceLanguage("vi-VN");
        session.setRoomName("live-abc");

        lenient().when(liveViewService.resolveViewer(STUDENT_EMAIL)).thenReturn(student);
        lenient().when(liveViewService.canView(student, session)).thenReturn(true);
        lenient().when(liveSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        lenient().when(liveLanguageTrackRepository.save(any(LiveLanguageTrack.class)))
                .thenAnswer(inv -> {
                    LiveLanguageTrack t = inv.getArgument(0);
                    if (t.getId() == null) {
                        ReflectionTestUtils.setField(t, "id", 500L);
                    }
                    return t;
                });
        lenient().when(liveKitConfig.getApiKey()).thenReturn("test-api-key");
        lenient().when(liveKitConfig.getApiSecret()).thenReturn("test-api-secret-32-chars-minimum");
        lenient().when(liveKitConfig.getServerUrl()).thenReturn("wss://test.livekit.cloud");
        lenient().when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai-api:8000");
    }

    private VoiceMapping voice(String voiceName, boolean isDefault) {
        VoiceMapping v = new VoiceMapping();
        v.setLanguage("ja-JP");
        v.setVoiceName(voiceName);
        v.setIsDefault(isDefault);
        v.setIsActive(true);
        return v;
    }

    @Test
    void activate_ngonNguChuaCoTrack_taoMoiVaGoiAiWorkerStart() {
        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("ja-JP"))
                .thenReturn(List.of(voice("ja-JP-NanamiNeural", true)));
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.empty());

        Res res = service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("ja-JP", null));

        assertThat(res.voiceName()).isEqualTo("ja-JP-NanamiNeural");
        assertThat(res.activeListenerCount()).isEqualTo(1);
        assertThat(res.trackName()).isEqualTo("translated-ja-JP");

        verify(restTemplate).postForEntity(
                contains("/admin/live-tracks/500/start"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void activate_ngonNguDaCoTrackACTIVE_chiTangSoNguoiNgheKhongGoiAiWorker() {
        LiveLanguageTrack existing = new LiveLanguageTrack();
        ReflectionTestUtils.setField(existing, "id", 501L);
        existing.setLiveSession(session);
        existing.setTargetLanguage("ja-JP");
        existing.setVoiceName("ja-JP-KeitaNeural");
        existing.setStatus(LiveTrackStatus.ACTIVE);
        existing.setActiveListenerCount(1);

        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("ja-JP"))
                .thenReturn(List.of(voice("ja-JP-NanamiNeural", true)));
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.of(existing));

        // Học viên thứ 2 gửi kèm voiceName khác — PHẢI bị bỏ qua, giữ nguyên giọng người đầu tiên chọn.
        Res res = service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("ja-JP", "ja-JP-NanamiNeural"));

        assertThat(res.voiceName()).isEqualTo("ja-JP-KeitaNeural");
        assertThat(res.activeListenerCount()).isEqualTo(2);
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void activate_ngonNguKhongActive_nemBusinessRuleViolation() {
        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("xx-XX")).thenReturn(List.of());

        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("xx-XX", null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /** Bug thật phát hiện lúc test tay F11.3: chọn trùng ngôn ngữ giảng viên đang nói khiến
     * Azure "dịch" gần như y nguyên câu gốc — nghe giống hệt audio gốc, dễ tưởng nhầm là lỗi
     * luồng audio. Giống BR-DUB-09 bên lồng tiếng video. */
    @Test
    void activate_trungNgonNguNguon_nemBusinessRuleViolation() {
        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("vi-VN", null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(voiceMappingRepository, never()).findByLanguageAndIsActiveTrue(anyString());
    }

    @Test
    void activate_phienChuaLive_bBiChan() {
        session.setStatus(LiveSessionStatus.SCHEDULED);

        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("ja-JP", null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void activate_guestChuaDangNhap_bBiChan() {
        when(liveViewService.resolveViewer(null)).thenReturn(null);

        assertThatThrownBy(() -> service.activate(null, SESSION_ID, new ActivateReq("ja-JP", null)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void activate_aiWorkerKhongKetNoiDuoc_nemExternalServiceException() {
        when(voiceMappingRepository.findByLanguageAndIsActiveTrue("ja-JP"))
                .thenReturn(List.of(voice("ja-JP-NanamiNeural", true)));
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.empty());
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("khong ket noi duoc"));

        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID, new ActivateReq("ja-JP", null)))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void deactivate_conNguoiNgheKhac_chiGiamSo() {
        LiveLanguageTrack track = new LiveLanguageTrack();
        ReflectionTestUtils.setField(track, "id", 502L);
        track.setTargetLanguage("ja-JP");
        track.setActiveListenerCount(2);
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.of(track));

        service.deactivate(STUDENT_EMAIL, SESSION_ID, "ja-JP");

        assertThat(track.getActiveListenerCount()).isEqualTo(1);
        assertThat(track.getStatus()).isEqualTo(LiveTrackStatus.ACTIVE);
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void deactivate_nguoiNgheCuoiCung_dungTrackVaGoiAiWorkerStop() {
        LiveLanguageTrack track = new LiveLanguageTrack();
        ReflectionTestUtils.setField(track, "id", 503L);
        track.setTargetLanguage("ja-JP");
        track.setActiveListenerCount(1);
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.of(track));

        service.deactivate(STUDENT_EMAIL, SESSION_ID, "ja-JP");

        assertThat(track.getStatus()).isEqualTo(LiveTrackStatus.STOPPED);
        assertThat(track.getActiveListenerCount()).isEqualTo(0);
        assertThat(track.getActiveFlag()).isNull();
        verify(restTemplate).postForEntity(
                contains("/admin/live-tracks/503/stop"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void deactivate_khongCoTrackNaoKhop_khongLamGiKhongLoi() {
        when(liveLanguageTrackRepository.findByLiveSession_IdAndTargetLanguageAndActiveFlag(SESSION_ID, "ja-JP", 1))
                .thenReturn(Optional.empty());

        service.deactivate(STUDENT_EMAIL, SESSION_ID, "ja-JP");

        verify(liveLanguageTrackRepository, never()).save(any());
    }

    @Test
    void stopAllTracksForSession_dungHetTrackDangACTIVE() {
        LiveLanguageTrack t1 = new LiveLanguageTrack();
        ReflectionTestUtils.setField(t1, "id", 601L);
        t1.setTargetLanguage("ja-JP");
        t1.setActiveListenerCount(3);
        LiveLanguageTrack t2 = new LiveLanguageTrack();
        ReflectionTestUtils.setField(t2, "id", 602L);
        t2.setTargetLanguage("en-US");
        t2.setActiveListenerCount(1);
        when(liveLanguageTrackRepository.findByLiveSession_IdAndStatus(SESSION_ID, LiveTrackStatus.ACTIVE))
                .thenReturn(List.of(t1, t2));

        service.stopAllTracksForSession(new LiveSessionEndedEvent(SESSION_ID));

        assertThat(t1.getStatus()).isEqualTo(LiveTrackStatus.STOPPED);
        assertThat(t2.getStatus()).isEqualTo(LiveTrackStatus.STOPPED);
        verify(restTemplate).postForEntity(contains("/admin/live-tracks/601/stop"), any(HttpEntity.class), eq(Void.class));
        verify(restTemplate).postForEntity(contains("/admin/live-tracks/602/stop"), any(HttpEntity.class), eq(Void.class));
    }
}
