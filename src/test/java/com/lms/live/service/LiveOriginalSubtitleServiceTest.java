package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.live.dto.LiveOriginalSubtitleDto.Res;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/** UC51 mở rộng (F11.5) — "Phụ đề gốc" độc lập với lồng tiếng, đếm người xem trực tiếp trên
 * {@code LiveSession} (không cần entity riêng vì không có chiều "ngôn ngữ" để dedupe). */
@ExtendWith(MockitoExtension.class)
class LiveOriginalSubtitleServiceTest {

    private static final String STUDENT_EMAIL = "student@lms.local";
    private static final Long SESSION_ID = 20L;
    private static final Long COURSE_ID = 10L;

    @Mock private LiveSessionRepository liveSessionRepository;
    @Mock private LiveViewService liveViewService;
    @Mock private LiveKitConfig liveKitConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    private LiveOriginalSubtitleService service;
    private User student;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new LiveOriginalSubtitleService(
                liveSessionRepository, liveViewService, liveKitConfig, restTemplate, aiWorkerConfig);
        ReflectionTestUtils.setField(service, "internalApiToken", "dev-internal-token");

        student = new User();
        student.setId(2L);
        student.setEmail(STUDENT_EMAIL);
        student.setRole(Role.STUDENT);

        User instructor = new User();
        instructor.setId(1L);

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
        lenient().when(liveSessionRepository.save(any(LiveSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(liveKitConfig.getApiKey()).thenReturn("test-api-key");
        lenient().when(liveKitConfig.getApiSecret()).thenReturn("test-api-secret-32-chars-minimum");
        lenient().when(liveKitConfig.getServerUrl()).thenReturn("wss://test.livekit.cloud");
        lenient().when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai-api:8000");
    }

    @Test
    void activate_lanDauTien_tangCountVaGoiAiWorkerStart() {
        Res res = service.activate(STUDENT_EMAIL, SESSION_ID);

        assertThat(res.listenerCount()).isEqualTo(1);
        verify(restTemplate).postForEntity(
                contains("/admin/live-transcription/" + SESSION_ID + "/start"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void activate_daCoNguoiXem_chiTangCountKhongGoiAiWorkerLaiLan2() {
        session.setOriginalSubtitleListenerCount(1);

        Res res = service.activate(STUDENT_EMAIL, SESSION_ID);

        assertThat(res.listenerCount()).isEqualTo(2);
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void activate_phienChuaLive_bBiChan() {
        session.setStatus(LiveSessionStatus.SCHEDULED);

        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void activate_guestChuaDangNhap_bBiChan() {
        when(liveViewService.resolveViewer(null)).thenReturn(null);

        assertThatThrownBy(() -> service.activate(null, SESSION_ID))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void activate_aiWorkerKhongKetNoiDuoc_nemExternalServiceException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("khong ket noi duoc"));

        assertThatThrownBy(() -> service.activate(STUDENT_EMAIL, SESSION_ID))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void deactivate_conNguoiXemKhac_chiGiamCount() {
        session.setOriginalSubtitleListenerCount(2);

        Res res = service.deactivate(STUDENT_EMAIL, SESSION_ID);

        assertThat(res.listenerCount()).isEqualTo(1);
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void deactivate_nguoiXemCuoiCung_goiAiWorkerStop() {
        session.setOriginalSubtitleListenerCount(1);

        Res res = service.deactivate(STUDENT_EMAIL, SESSION_ID);

        assertThat(res.listenerCount()).isEqualTo(0);
        verify(restTemplate).postForEntity(
                contains("/admin/live-transcription/" + SESSION_ID + "/stop"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void stopOnSessionEnded_dangCoNguoiXem_dungLuongVaGoiAiWorkerStop() {
        session.setOriginalSubtitleListenerCount(3);

        service.stopOnSessionEnded(new LiveSessionEndedEvent(SESSION_ID));

        assertThat(session.getOriginalSubtitleListenerCount()).isZero();
        verify(restTemplate).postForEntity(
                contains("/admin/live-transcription/" + SESSION_ID + "/stop"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void stopOnSessionEnded_khongCoAiXem_khongLamGiKhongGoiAiWorker() {
        session.setOriginalSubtitleListenerCount(0);

        service.stopOnSessionEnded(new LiveSessionEndedEvent(SESSION_ID));

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }
}
