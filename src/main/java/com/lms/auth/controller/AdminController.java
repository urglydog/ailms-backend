package com.lms.auth.controller;

import com.lms.auth.dto.AuthResponseDto;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.AuthService;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.repository.AiUsageLogRepository;
import com.lms.common.service.NotificationService;
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
    private final AuthService authService;
    private final NotificationService notificationService;

    public record SpamFlagReq(boolean flagged, String reason) {}

    /** Chỉ Admin thấy — không public, không tự động khóa toàn bộ tài khoản (độc lập với
     * isActive). Cascade (23/10/2026): đánh dấu spam kéo theo khóa AI luôn — xem docblock
     * AccountRestrictionService. Bỏ đánh dấu spam KHÔNG tự mở khóa AI lại. */
    @PutMapping("/users/{userId}/spam-flag")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> setSpamFlag(@PathVariable Long userId, @RequestBody SpamFlagReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setIsSpamFlagged(req.flagged());
        user.setSpamFlagReason(req.flagged() ? req.reason() : null);
        if (req.flagged()) {
            user.setIsAiLocked(true);
        }
        userRepository.save(user);
        if (req.flagged()) {
            notificationService.notify(userId, "SPAM_FLAGGED", "Tài khoản bị hạn chế",
                    "Tài khoản của bạn đã bị đánh dấu vi phạm chính sách và bị khóa tính năng AI. Lý do: " + req.reason(),
                    "/profile");
        }
        return ResponseEntity.ok().build();
    }

    /** Hỗ trợ user khi quên-mật-khẩu-nhưng-email/OTP không nhận được (23/10/2026) — sinh link
     * reset token (khác hẳn OTP email hiện có) để Admin tự copy gửi thủ công qua kênh khác. */
    @PostMapping("/users/{userId}/send-reset-link")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDto.ResetLinkRes> sendResetLink(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return ResponseEntity.ok(authService.generateAdminResetLink(user.getEmail()));
    }

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
        if (isLocked) {
            notificationService.notify(userId, "AI_LOCKED", "Tính năng AI đã bị khóa",
                    "Tài khoản của bạn đã bị khóa tính năng AI do vi phạm chính sách sử dụng.", "/profile");
        }
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
        notificationService.notify(userId, "AI_LOCKED", "Tính năng AI đã bị khóa",
                "Tài khoản của bạn đã bị khóa tính năng AI do vi phạm chính sách sử dụng.", "/profile");
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
