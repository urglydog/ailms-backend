package com.lms.auth.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;

    @GetMapping("/ai-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAiUsageSummary() {
        return ResponseEntity.ok(aiUsageLogRepository.getUsageSummaryByUser());
    }

    @PutMapping("/users/{userId}/toggle-ai-lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> toggleAiLock(@PathVariable Long userId, @RequestParam boolean isLocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setIsAiLocked(isLocked);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
