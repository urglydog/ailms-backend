package com.lms.chat.controller;

import com.lms.common.config.AiWorkerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.security.Principal;
import java.util.Map;

/**
 * AI Assistant riêng cho Instructor — TÁCH BIỆT hoàn toàn khỏi Discovery Chat (UC49).
 *
 * <p>Instructor không cần "tìm khóa học để học" — họ cần trợ lý giúp:
 * <ul>
 *   <li>Phân tích dữ liệu khóa học của chính mình (doanh thu, học viên)</li>
 *   <li>Gợi ý cải thiện nội dung dựa trên đánh giá của học viên</li>
 *   <li>Hỗ trợ tạo mô tả, tiêu đề hấp dẫn cho khóa học</li>
 * </ul>
 *
 * <p><b>Bảo mật:</b> {@code @PreAuthorize("hasRole('INSTRUCTOR')")} — chỉ Instructor truy cập được.
 * Student gọi vào đây sẽ nhận 403.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/instructor/ai-assistant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorAiController {

    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;

    /**
     * POST /api/v1/instructor/ai-assistant/chat
     *
     * <p>Forward vào AI Worker endpoint {@code /api/v1/instructor/chat} với:
     * <ul>
     *   <li>{@code caller_role = "INSTRUCTOR"}</li>
     *   <li>{@code instructor_email} để AI Worker có thể fetch data của instructor này</li>
     * </ul>
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            Principal principal,
            @RequestBody Map<String, Object> body) {

        String instructorEmail = principal.getName();

        Map<String, Object> aiPayload = Map.of(
                "message", body.getOrDefault("message", ""),
                "caller_role", "INSTRUCTOR",
                "instructor_email", instructorEmail
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiPayload, headers);

        try {
            ResponseEntity<Object> aiResponse = restTemplate.postForEntity(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/instructor/chat",
                    entity,
                    Object.class
            );
            return ResponseEntity.status(aiResponse.getStatusCode()).body(aiResponse.getBody());
        } catch (Exception e) {
            log.error("AI Worker call failed for Instructor AI: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI service hiện không khả dụng. Vui lòng thử lại sau."));
        }
    }
}
