package com.lms.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;

/**
 * UC49 — Course Discovery Chat Proxy.
 *
 * <p>Backend là điểm vào DUY NHẤT cho Discovery Chat — KHÔNG để FE gọi thẳng AI Worker.
 * Lý do:
 * <ul>
 *   <li>Kiểm tra quota (BR-DISCOVERY-01: Guest 15/IP/h, Student 30/ngày)</li>
 *   <li>Truyền role context vào AI Worker để agent biết caller là ai</li>
 *   <li>AI Worker là internal service, không expose ra internet</li>
 * </ul>
 *
 * <p>Endpoint này PUBLIC (không cần JWT) — Guest cũng gọi được.
 * Nếu có JWT hợp lệ, Spring sẽ điền Authentication và ta biết role của caller.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final RestTemplate restTemplate;
    private final com.lms.common.config.AiWorkerConfig aiWorkerConfig;

    /**
     * POST /api/v1/discovery/chat
     *
     * <ul>
     *   <li>Guest (no JWT): role = "GUEST", quota = 15 tin/IP/h (TODO: Redis counter)</li>
     *   <li>Student (JWT): role = "STUDENT", quota = 30 tin/ngày</li>
     *   <li>Instructor (JWT): redirect → 403, dùng /api/v1/instructor/ai-assistant/chat thay thế</li>
     *   <li>Admin (JWT): 403 — admin không dùng Discovery Chat</li>
     * </ul>
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            Authentication authentication,
            @RequestBody Map<String, Object> body,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // ── Xác định role của caller ──────────────────────────────────────
        String role = "GUEST";
        if (authentication != null && authentication.isAuthenticated()) {
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            String authority = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_GUEST");

            // Instructor và Admin không dùng Discovery — redirect về đúng agent
            if (authority.contains("INSTRUCTOR")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                            "error", "Instructor nên dùng /api/v1/instructor/ai-assistant/chat",
                            "redirect", "/api/v1/instructor/ai-assistant/chat"
                        ));
            }
            if (authority.contains("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Admin không sử dụng Course Discovery Chat."));
            }
            role = "STUDENT";
        }

        // ── TODO BR-DISCOVERY-01: quota check qua Redis ───────────────────
        // Guest: key = "discovery:ip:<ip>", TTL 1h, limit 15
        // Student: key = "discovery:user:<userId>", TTL 1 ngày, limit 30
        // Hiện tại bỏ qua để không block dev — sẽ implement cùng BR-TUTOR-04

        // ── Forward vào AI Worker với role context ────────────────────────
        Map<String, Object> aiPayload = Map.of(
                "message", body.getOrDefault("message", ""),
                "caller_role", role
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiPayload, headers);

        try {
            ResponseEntity<Object> aiResponse = restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/discovery/chat",
                    entity,
                    Object.class
            );
            return ResponseEntity.status(aiResponse.getStatusCode()).body(aiResponse.getBody());
        } catch (Exception e) {
            log.error("AI Worker call failed for Discovery Chat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI service hiện không khả dụng. Vui lòng thử lại sau."));
        }
    }
}
