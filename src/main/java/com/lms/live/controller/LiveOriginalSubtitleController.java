package com.lms.live.controller;

import com.lms.live.dto.LiveOriginalSubtitleDto.Res;
import com.lms.live.service.LiveOriginalSubtitleService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC51 mở rộng (F11.5) — bật/tắt "Phụ đề gốc", độc lập hoàn toàn với lồng tiếng. Bắt buộc JWT
 * (BR-LIVE-02) giống kích hoạt ngôn ngữ dịch ở {@code LiveLanguageTrackController}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/live-sessions/{sessionId}/original-subtitle")
public class LiveOriginalSubtitleController {

    private final LiveOriginalSubtitleService liveOriginalSubtitleService;

    @PostMapping
    public ResponseEntity<Res> activate(Principal principal, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveOriginalSubtitleService.activate(principal.getName(), sessionId));
    }

    @DeleteMapping
    public ResponseEntity<Res> deactivate(Principal principal, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveOriginalSubtitleService.deactivate(principal.getName(), sessionId));
    }
}
