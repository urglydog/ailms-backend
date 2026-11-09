package com.lms.common.controller;

import com.lms.common.entity.AiPromptSecurityFlag;
import com.lms.common.repository.AiPromptSecurityFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller nội bộ nhận cờ cảnh báo Prompt Injection/DoW từ AI Worker (Lớp 2b, xem
 * {@code ai-worker/app/services/security_heuristics.py}). Không bị chặn bởi JWT filter nhờ
 * đường dẫn /api/internal/** (xem {@code InternalApiTokenFilter}).
 */
@RestController
@RequestMapping("/api/internal/ai-security")
@RequiredArgsConstructor
public class InternalAiSecurityController {

    private final AiPromptSecurityFlagRepository aiPromptSecurityFlagRepository;

    public record FlagReq(String source, String userEmail, String matchedPattern, String messageSnapshot) {
    }

    @PostMapping("/flag")
    public ResponseEntity<Void> flag(@RequestBody FlagReq req) {
        AiPromptSecurityFlag flag = new AiPromptSecurityFlag();
        flag.setSource(req.source());
        flag.setUserEmail(req.userEmail());
        flag.setMatchedPattern(req.matchedPattern());
        flag.setMessageSnapshot(req.messageSnapshot());
        aiPromptSecurityFlagRepository.save(flag);
        return ResponseEntity.ok().build();
    }
}
