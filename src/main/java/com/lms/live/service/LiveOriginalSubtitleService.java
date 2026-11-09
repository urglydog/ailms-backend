package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.live.dto.LiveOriginalSubtitleDto.Res;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * UC51 mở rộng (F11.5) — "Phụ đề gốc" ĐỘC LẬP với lồng tiếng: trước đây phụ đề chỉ là sản phẩm phụ
 * của {@link LiveLanguageTrackService} (không ai chọn ngôn ngữ dịch thì không có Azure nào chạy,
 * không có chữ nào để hiện). Ở đây chạy 1 luồng {@code SpeechRecognizer} THUẦN (không dịch, không
 * tổng hợp giọng — rẻ và nhanh hơn {@code TranslationRecognizer}) bên AI Worker, chỉ để có chữ gốc
 * chạy dần theo `recognizing` (kiểu phụ đề YouTube live).
 *
 * <p>Không cần entity/bảng riêng như {@code LiveLanguageTrack} — chỉ 1 luồng/phiên (không có chiều
 * "ngôn ngữ" để cần UNIQUE/dedupe), nên 1 counter ngay trên {@link LiveSession} là đủ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveOriginalSubtitleService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveViewService liveViewService;
    private final LiveKitConfig liveKitConfig;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    @Value("${lms.internal.api-token}")
    private String internalApiToken;

    private static String transcriberIdentity(Long sessionId) {
        return "transcriber-" + sessionId;
    }

    @Transactional
    public Res activate(String viewerEmail, Long sessionId) {
        User viewer = requireViewer(viewerEmail);
        LiveSession session = requireLiveSession(sessionId);
        requireCanView(viewer, session);

        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BusinessRuleViolationException(
                    "Phiên chưa live (hiện tại: " + session.getStatus() + "), chưa có gì để phiên dịch");
        }

        int newCount = session.getOriginalSubtitleListenerCount() + 1;
        session.setOriginalSubtitleListenerCount(newCount);
        liveSessionRepository.save(session);

        if (newCount == 1) {
            startTranscriptionAgent(session);
        }
        return new Res(newCount);
    }

    @Transactional
    public Res deactivate(String viewerEmail, Long sessionId) {
        requireViewer(viewerEmail);
        LiveSession session = requireLiveSession(sessionId);

        int newCount = Math.max(0, session.getOriginalSubtitleListenerCount() - 1);
        session.setOriginalSubtitleListenerCount(newCount);
        liveSessionRepository.save(session);

        if (newCount == 0) {
            stopTranscriptionAgent(session.getId());
        }
        return new Res(newCount);
    }

    /** BR-LIVE-09 phần phụ đề gốc — phiên kết thúc thì luồng nhận diện gốc cũng phải dừng theo,
     * giống hệt lý do {@code LiveLanguageTrackService.stopAllTracksForSession} tồn tại. */
    @EventListener
    @Transactional
    public void stopOnSessionEnded(LiveSessionEndedEvent event) {
        LiveSession session = liveSessionRepository.findById(event.liveSessionId()).orElse(null);
        if (session == null || session.getOriginalSubtitleListenerCount() == 0) {
            return;
        }
        session.setOriginalSubtitleListenerCount(0);
        liveSessionRepository.save(session);
        stopTranscriptionAgent(session.getId());
    }

    private void startTranscriptionAgent(LiveSession session) {
        AccessToken agentToken = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        agentToken.setIdentity(transcriberIdentity(session.getId()));
        agentToken.setName("Transcriber");
        // Không canPublish (khong phat audio/video, chi phat chu qua Data Message).
        agentToken.addGrants(new RoomJoin(true), new RoomName(session.getRoomName()),
                new CanSubscribe(true), new CanPublishData(true));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", session.getRoomName());
        payload.put("serverUrl", liveKitConfig.getServerUrl());
        payload.put("agentToken", agentToken.toJwt());
        payload.put("instructorIdentity", LiveSessionService.instructorIdentity(session.getInstructor().getId()));
        payload.put("sourceLanguage", session.getSourceLanguage());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/admin/live-transcription/" + session.getId() + "/start",
                    entity, Void.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException(
                    "Không mở được luồng phụ đề gốc, thử lại sau ít phút: " + e.getMessage());
        }
    }

    /** Best-effort — giống hệt lý do {@code LiveLanguageTrackService.stopTranslationAgent}. */
    private void stopTranscriptionAgent(Long sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/admin/live-transcription/" + sessionId + "/stop",
                    entity, Void.class);
        } catch (RestClientException e) {
            log.warn("Khong bao duoc AI Worker dung Transcription Agent cho session {} (khong anh huong trang thai DB): {}",
                    sessionId, e.getMessage());
        }
    }

    private User requireViewer(String email) {
        User viewer = liveViewService.resolveViewer(email);
        if (viewer == null) {
            throw new AccessDeniedDomainException("Cần đăng nhập để dùng phụ đề live (BR-LIVE-02)");
        }
        return viewer;
    }

    private void requireCanView(User viewer, LiveSession session) {
        if (!liveViewService.canView(viewer, session)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-LIVE-01)");
        }
    }

    private LiveSession requireLiveSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession", sessionId));
    }
}
