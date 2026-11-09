package com.lms.auth.controller;

import com.lms.auth.service.AiLockScanService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback nội bộ cho Celery beat của AI-worker gọi định kỳ — xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter}, giống mọi {@code /api/internal/**}
 * khác. Xem {@code app/tasks/maintenance.py::scan_ai_lock_proposals}.
 */
@RestController
@RequestMapping("/api/internal/ai-lock")
@RequiredArgsConstructor
public class InternalAiLockController {

    private final AiLockScanService aiLockScanService;

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scan() {
        int created = aiLockScanService.scan();
        return ResponseEntity.ok(Map.of("proposalsCreated", created));
    }
}
