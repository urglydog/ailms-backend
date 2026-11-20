package com.lms.common.controller;

import com.lms.auth.repository.UserRepository;
import com.lms.common.entity.AiUsageLog;
import com.lms.common.repository.AiUsageLogRepository;
import com.lms.common.service.AiPricing;
import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhận số liệu dùng AI từ AI Worker cho MỌI lệnh gọi Gemini/Groq (21/11/2026) để theo dõi chi phí thật. Chỉ AI Worker gọi được (header {@code X-Internal-Token}, đường
 * {@code /api/internal/**}). Dữ liệu đầu vào được kiểm chặt (danh sách trắng nhà cung cấp, mẫu tên tính năng, giới hạn số) và chi phí do BE tự tính từ cấu hình đơn giá,
 * không tin số tiền do bên gửi báo.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/ai-usage")
@RequiredArgsConstructor
public class InternalAiUsageController {

    static final Set<String> PROVIDERS = Set.of("GEMINI", "GROQ");
    static final Pattern FEATURE = Pattern.compile("^[A-Z0-9_]{2,40}$");
    static final long MAX_TOKENS = 20_000_000L;
    static final long MAX_AUDIO_SECONDS = 86_400L;

    private final AiUsageLogRepository repository;
    private final UserRepository userRepository;
    private final AiPricing pricing;

    public record UsageReq(String provider, String feature, String model, Long promptTokens, Long completionTokens, Long thinkingTokens, Long audioSeconds,
                           String userEmail, Long userId, Long courseId, Long lessonId, Boolean estimated, Boolean embedding) {}

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody UsageReq req) {
        String provider = req.provider() == null ? "GEMINI" : req.provider().toUpperCase(java.util.Locale.ROOT);
        if (!PROVIDERS.contains(provider) || req.feature() == null || !FEATURE.matcher(req.feature()).matches()) {
            return ResponseEntity.badRequest().build();
        }
        long prompt = clamp(req.promptTokens(), MAX_TOKENS);
        long completion = clamp(req.completionTokens(), MAX_TOKENS);
        long thinking = clamp(req.thinkingTokens(), MAX_TOKENS);
        long audio = clamp(req.audioSeconds(), MAX_AUDIO_SECONDS);

        AiUsageLog log = new AiUsageLog();
        log.setProvider(provider);
        log.setFeatureType(req.feature());
        log.setModel(req.model() == null ? null : req.model().substring(0, Math.min(80, req.model().length())));
        log.setPromptTokens((int) prompt);
        log.setCompletionTokens((int) completion);
        log.setThinkingTokens((int) thinking);
        log.setTotalTokens((int) Math.min(Integer.MAX_VALUE, prompt + completion + thinking));
        log.setAudioSeconds((int) audio);
        log.setCourseId(req.courseId());
        log.setLessonId(req.lessonId());
        log.setEstimated(Boolean.TRUE.equals(req.estimated()));
        log.setUserId(resolveUser(req));
        BigDecimal cost = audio > 0 ? pricing.audioCost(audio)
                : Boolean.TRUE.equals(req.embedding()) ? pricing.embeddingCost(prompt) : pricing.textCost(log.getModel(), null, prompt, completion, thinking);
        log.setCostUsd(cost);
        repository.save(log);
        return ResponseEntity.ok().build();
    }

    private Long resolveUser(UsageReq req) {
        if (req.userId() != null && userRepository.existsById(req.userId())) {
            return req.userId();
        }
        if (req.userEmail() != null && !req.userEmail().isBlank()) {
            return userRepository.findByEmail(req.userEmail().trim()).map(u -> u.getId()).orElse(null);
        }
        return null;
    }

    private static long clamp(Long v, long max) {
        return v == null ? 0 : Math.max(0, Math.min(v, max));
    }
}
