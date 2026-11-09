package com.lms.live.controller;

import com.lms.live.dto.LiveLanguageTrackDto.ActivateReq;
import com.lms.live.dto.LiveLanguageTrackDto.Res;
import com.lms.live.repository.LiveLanguageTrackRepository;
import com.lms.live.enums.LiveTrackStatus;
import com.lms.live.service.LiveLanguageTrackService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UC52 — kích hoạt/rời ngôn ngữ lồng tiếng live (BR-LIVE-05/06). Kích hoạt/rời bắt buộc JWT
 * (BR-LIVE-02 — Guest bị chặn hành động tương tác, khác việc XEM ở {@code LiveViewController}).
 * GET danh sách track đang chạy là public (badge hiển thị cho cả Guest trước khi họ đăng nhập).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/live-sessions/{sessionId}/language-tracks")
public class LiveLanguageTrackController {

    private final LiveLanguageTrackService liveLanguageTrackService;
    private final LiveLanguageTrackRepository liveLanguageTrackRepository;

    @GetMapping
    public ResponseEntity<List<Res>> listActive(@PathVariable Long sessionId) {
        List<Res> tracks = liveLanguageTrackRepository
                .findByLiveSession_IdAndStatus(sessionId, LiveTrackStatus.ACTIVE).stream()
                .map(t -> new Res(t.getId(), t.getTargetLanguage(), t.getVoiceName(), t.getStatus(),
                        t.getActiveListenerCount(), LiveLanguageTrackService.trackNameFor(t.getTargetLanguage())))
                .toList();
        return ResponseEntity.ok(tracks);
    }

    @PostMapping
    public ResponseEntity<Res> activate(
            Principal principal, @PathVariable Long sessionId, @Valid @RequestBody ActivateReq req) {
        return ResponseEntity.ok(liveLanguageTrackService.activate(principal.getName(), sessionId, req));
    }

    @DeleteMapping("/{targetLanguage}")
    public ResponseEntity<Void> deactivate(
            Principal principal, @PathVariable Long sessionId, @PathVariable String targetLanguage) {
        liveLanguageTrackService.deactivate(principal.getName(), sessionId, targetLanguage);
        return ResponseEntity.noContent().build();
    }
}
