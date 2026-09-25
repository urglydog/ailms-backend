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

    /** Task 11A — tổng token/chi phí AI theo ngày (30 ngày gần nhất), phục vụ biểu đồ Admin Dashboard. */
    @GetMapping("/ai-usage/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAiUsageDaily() {
        return ResponseEntity.ok(aiUsageLogRepository.getDailyUsage(java.time.LocalDateTime.now().minusDays(30)));
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

    /** Auto-ban bằng AI (25/09/2026) — danh sách user có đề xuất khoá đang chờ Admin xử lý. */
    @GetMapping("/ai-lock-proposals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAiLockProposals() {
        return ResponseEntity.ok(userRepository.findByAiLockProposedAtIsNotNull().stream()
                .map(u -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("userId", u.getId());
                    row.put("email", u.getEmail());
                    row.put("fullName", u.getFullName());
                    row.put("aiLockProposedAt", u.getAiLockProposedAt());
                    row.put("aiLockProposedReason", u.getAiLockProposedReason());
                    return row;
                })
                .toList());
    }

    /** Admin đồng ý đề xuất — khoá thật (dùng lại đúng {@code isAiLocked}), xoá đề xuất. */
    @PostMapping("/users/{userId}/confirm-lock-proposal")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> confirmLockProposal(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setIsAiLocked(true);
        user.setAiLockProposedAt(null);
        user.setAiLockProposedReason(null);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /** Admin bỏ qua đề xuất — không khoá, chỉ xoá đề xuất (job có thể tự tạo lại sau nếu tín
     * hiệu vẫn còn ở lần quét tiếp theo). */
    @PostMapping("/users/{userId}/dismiss-lock-proposal")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> dismissLockProposal(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setAiLockProposedAt(null);
        user.setAiLockProposedReason(null);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
