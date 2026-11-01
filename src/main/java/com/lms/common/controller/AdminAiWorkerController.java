package com.lms.common.controller;

import com.lms.common.config.AiWorkerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Proxy nội bộ cho trang Admin "Cấu hình hệ thống" / "Giám sát AI Queue" gọi AI Worker.
 *
 * <p>AI Worker là internal service, FE KHÔNG được gọi trực tiếp (xem {@link AiWorkerConfig}) —
 * trước đây FE tự fetch thẳng tới {@code NEXT_PUBLIC_AI_WORKER_URL} (mặc định
 * {@code localhost:8002}), nên trong trình duyệt của người xem admin lại trỏ về máy họ
 * chứ không phải server → luôn báo "Không kết nối được" dù AI Worker thật vẫn sống (25/10/2026).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-worker")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiWorkerController {

    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    @Value("${lms.internal.api-token}")
    private String internalApiToken;

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            ResponseEntity<Object> res = restTemplate.getForEntity(
                    aiWorkerConfig.getBaseUrl() + "/health", Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            log.error("AI Worker health check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI Worker hiện không kết nối được."));
        }
    }

    @GetMapping("/queue-stats")
    public ResponseEntity<?> queueStats() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Object> res = restTemplate.exchange(
                    aiWorkerConfig.getBaseUrl() + "/admin/queue-stats",
                    HttpMethod.GET, entity, Object.class);
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (Exception e) {
            log.error("AI Worker queue-stats failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI Worker hiện không kết nối được."));
        }
    }
}
