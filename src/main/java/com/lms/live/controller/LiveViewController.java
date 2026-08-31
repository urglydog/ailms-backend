package com.lms.live.controller;

import com.lms.live.dto.LiveViewDto.DetailRes;
import com.lms.live.dto.LiveViewDto.SummaryRes;
import com.lms.live.service.LiveViewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC51 — xem phiên Live (Guest/Student/Instructor/Admin), phân quyền BR-LIVE-01.
 *
 * <p>PUBLIC endpoint (xem {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}) — KHÔNG bắt buộc JWT vì
 * Guest phải gọi được với phiên {@code PUBLIC}, giống hệt mô hình {@code DiscoveryController}
 * (UC49): {@link Authentication} có thể {@code null}, quyền xem thật sự lọc ở tầng service
 * ({@code LiveViewService.canView}), không chỉ dựa vào Spring Security.
 */
@RestController
@RequiredArgsConstructor
public class LiveViewController {

    private final LiveViewService liveViewService;

    @GetMapping("/api/v1/courses/{courseId}/live-sessions")
    public ResponseEntity<List<SummaryRes>> listForCourse(Authentication authentication, @PathVariable Long courseId) {
        return ResponseEntity.ok(liveViewService.listForCourse(emailOf(authentication), courseId));
    }

    @GetMapping("/api/v1/live-sessions/{sessionId}/view")
    public ResponseEntity<DetailRes> view(Authentication authentication, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveViewService.view(emailOf(authentication), sessionId));
    }

    private String emailOf(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
    }
}
