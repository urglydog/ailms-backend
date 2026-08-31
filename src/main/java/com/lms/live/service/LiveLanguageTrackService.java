package com.lms.live.service;

import com.lms.auth.entity.User;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.config.LiveKitConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ExternalServiceException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.live.dto.LiveLanguageTrackDto.ActivateReq;
import com.lms.live.dto.LiveLanguageTrackDto.Res;
import com.lms.live.entity.LiveLanguageTrack;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveTrackStatus;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveLanguageTrackRepository;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * UC52 (F11.3) — kích hoạt/tham gia/rời ngôn ngữ lồng tiếng live, theo BR-LIVE-05/06. Tên track
 * LiveKit LUÔN suy ra được từ ngôn ngữ ({@link #trackNameFor}) — không lưu riêng, FE tự tính lại
 * y hệt để subscribe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveLanguageTrackService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveLanguageTrackRepository liveLanguageTrackRepository;
    private final VoiceMappingRepository voiceMappingRepository;
    private final LiveViewService liveViewService;
    private final LiveKitConfig liveKitConfig;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    @Value("${lms.internal.api-token}")
    private String internalApiToken;

    public static String trackNameFor(String targetLanguage) {
        return "translated-" + targetLanguage;
    }

    private static String agentIdentity(Long trackId) {
        return "translator-" + trackId;
    }

    /**
     * BR-LIVE-05 — track chưa có thì tạo mới + gọi AI Worker mở agent dịch THẬT (đồng bộ: lỗi ở
     * bước này phải làm rollback, không để lại track "ACTIVE" mà không có agent nào chạy, học
     * viên sẽ không nghe được gì). Track đã có thì chỉ tăng {@code activeListenerCount}, KHÔNG
     * gọi AI Worker lần nào nữa (agent đã chạy sẵn cho track đó).
     */
    @Transactional
    public Res activate(String viewerEmail, Long sessionId, ActivateReq req) {
        User viewer = requireViewer(viewerEmail);
        LiveSession session = requireLiveSession(sessionId);
        requireCanView(viewer, session);

        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BusinessRuleViolationException(
                    "Phiên chưa live (hiện tại: " + session.getStatus() + "), chưa có gì để lồng tiếng");
        }

        String targetLanguage = req.targetLanguage();
        // Giống BR-DUB-09 bên lồng tiếng video (DubbingRequestService) — dịch một ngôn ngữ sang
        // CHÍNH NÓ vô nghĩa: Azure Speech Translation coi đây là "dịch" hợp lệ (không lỗi), chỉ
        // đọc lại gần như y nguyên câu gốc — nghe giống hệt audio gốc, dễ tưởng nhầm là bug ở
        // luồng audio (đã xảy ra thật lúc test F11.3: phiên live sourceLanguage=vi-VN, học viên
        // chọn target=vi-VN, nghe ra y hệt gốc).
        if (targetLanguage.equalsIgnoreCase(session.getSourceLanguage())) {
            throw new BusinessRuleViolationException(
                    "Ngôn ngữ lồng tiếng phải khác ngôn ngữ giảng viên đang nói (" + session.getSourceLanguage() + ")");
        }

        List<VoiceMapping> activeVoices = voiceMappingRepository.findByLanguageAndIsActiveTrue(targetLanguage);
        if (activeVoices.isEmpty()) {
            throw new BusinessRuleViolationException("Ngôn ngữ này chưa được kích hoạt lồng tiếng (BR-LIVE-06)");
        }

        Optional<LiveLanguageTrack> existing = liveLanguageTrackRepository
                .findByLiveSession_IdAndTargetLanguageAndActiveFlag(sessionId, targetLanguage, 1);
        if (existing.isPresent()) {
            LiveLanguageTrack track = existing.get();
            track.setActiveListenerCount(track.getActiveListenerCount() + 1);
            liveLanguageTrackRepository.save(track);
            return toRes(track);
        }

        VoiceMapping voice = resolveVoice(activeVoices, req.voiceName());

        LiveLanguageTrack track = new LiveLanguageTrack();
        track.setLiveSession(session);
        track.setTargetLanguage(targetLanguage);
        track.setVoiceName(voice.getVoiceName());
        track.setStatus(LiveTrackStatus.ACTIVE);
        track.setActiveListenerCount(1);
        LiveLanguageTrack saved = liveLanguageTrackRepository.save(track);

        startTranslationAgent(session, saved);

        return toRes(saved);
    }

    /**
     * BR-LIVE-05 — rời hoặc đổi ngôn ngữ khác. Không có track ACTIVE nào khớp thì coi là
     * đã dừng từ trước (vd. phiên vừa kết thúc, {@link #stopAllTracksForSession} đã dọn) —
     * idempotent, không lỗi, để FE gọi dọn lúc unmount thoải mái không cần kiểm tra trước.
     */
    @Transactional
    public void deactivate(String viewerEmail, Long sessionId, String targetLanguage) {
        requireViewer(viewerEmail);

        liveLanguageTrackRepository
                .findByLiveSession_IdAndTargetLanguageAndActiveFlag(sessionId, targetLanguage, 1)
                .ifPresent(this::decrementOrStop);
    }

    /** BR-LIVE-09 phần lồng tiếng — phiên kết thúc (chủ động hoặc tự động) thì mọi track dịch
     * đang chạy phải dừng theo, không để agent chạy vô thời hạn tốn tài nguyên Azure. */
    @EventListener
    @Transactional
    public void stopAllTracksForSession(LiveSessionEndedEvent event) {
        List<LiveLanguageTrack> tracks = liveLanguageTrackRepository
                .findByLiveSession_IdAndStatus(event.liveSessionId(), LiveTrackStatus.ACTIVE);
        tracks.forEach(this::forceStop);
    }

    private void decrementOrStop(LiveLanguageTrack track) {
        int remaining = Math.max(0, track.getActiveListenerCount() - 1);
        track.setActiveListenerCount(remaining);
        if (remaining == 0) {
            forceStop(track);
        } else {
            liveLanguageTrackRepository.save(track);
        }
    }

    private void forceStop(LiveLanguageTrack track) {
        track.setStatus(LiveTrackStatus.STOPPED);
        track.setActiveListenerCount(0);
        track.releaseActiveFlag();
        liveLanguageTrackRepository.save(track);
        stopTranslationAgent(track.getId());
    }

    /** Đồng bộ có chủ đích — xem javadoc {@link #activate}. */
    private void startTranslationAgent(LiveSession session, LiveLanguageTrack track) {
        String agentIdentity = agentIdentity(track.getId());
        AccessToken agentToken = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        agentToken.setIdentity(agentIdentity);
        agentToken.setName("Translator");
        // canPublishData — agent phát phụ đề gốc/dịch thời gian thực qua Data Message (giống chat
        // F11.4, không lưu CSDL) khi Azure chốt xong 1 câu.
        agentToken.addGrants(new RoomJoin(true), new RoomName(session.getRoomName()),
                new CanPublish(true), new CanSubscribe(true), new CanPublishData(true));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", session.getRoomName());
        payload.put("serverUrl", liveKitConfig.getServerUrl());
        payload.put("agentToken", agentToken.toJwt());
        payload.put("instructorIdentity", LiveSessionService.instructorIdentity(session.getInstructor().getId()));
        payload.put("sourceLanguage", session.getSourceLanguage());
        payload.put("targetLanguage", track.getTargetLanguage());
        payload.put("voiceName", track.getVoiceName());
        payload.put("trackName", trackNameFor(track.getTargetLanguage()));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/admin/live-tracks/" + track.getId() + "/start", entity, Void.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException(
                    "Không mở được luồng lồng tiếng thời gian thực, thử lại sau ít phút: " + e.getMessage());
        }
    }

    /** Best-effort giống {@code DubbingRequestService.requestAiWorkerCancel} — DB đã ghi STOPPED
     * rồi, lỗi ở bước gọi AI Worker chỉ khiến agent chạy dư vài giây rồi tự thoát khi mất track
     * nguồn (giảng viên), không ảnh hưởng trải nghiệm của học viên vừa rời. */
    private void stopTranslationAgent(Long trackId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/admin/live-tracks/" + trackId + "/stop", entity, Void.class);
        } catch (RestClientException e) {
            log.warn("Khong bao duoc AI Worker dung Translation Agent cho track {} (khong anh huong trang thai DB): {}",
                    trackId, e.getMessage());
        }
    }

    private VoiceMapping resolveVoice(List<VoiceMapping> activeVoices, String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return activeVoices.stream().filter(VoiceMapping::getIsDefault).findFirst()
                    .orElse(activeVoices.get(0));
        }
        return activeVoices.stream()
                .filter(v -> voiceName.equals(v.getVoiceName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Giọng đọc \"" + voiceName + "\" không khả dụng cho ngôn ngữ này"));
    }

    private User requireViewer(String email) {
        User viewer = liveViewService.resolveViewer(email);
        if (viewer == null) {
            throw new AccessDeniedDomainException("Cần đăng nhập để dùng lồng tiếng live (BR-LIVE-02)");
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

    private Res toRes(LiveLanguageTrack track) {
        return new Res(track.getId(), track.getTargetLanguage(), track.getVoiceName(), track.getStatus(),
                track.getActiveListenerCount(), trackNameFor(track.getTargetLanguage()));
    }
}
