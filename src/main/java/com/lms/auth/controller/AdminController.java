package com.lms.auth.controller;

import com.lms.auth.dto.AuthResponseDto;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.AuthService;
import com.lms.common.exception.BusinessRuleViolationException;
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
     * isActive). Cascade ĐỐI XỨNG 2 CHIỀU (23/10/2026, sửa theo phản hồi test thật — bản trước
     * chỉ cascade lúc đánh dấu, lúc bỏ đánh dấu phải tự mở AI riêng): đánh dấu spam kéo theo
     * khóa AI; bỏ đánh dấu spam kéo theo mở khóa AI luôn — xem docblock AccountRestrictionService. */
    @PutMapping("/users/{userId}/spam-flag")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> setSpamFlag(@PathVariable Long userId, @RequestBody SpamFlagReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        // (23/10/2026, sửa bug thật — phát hiện qua test: DB thực tế có user isActive=0 nhưng
        // isAiLocked=0/isSpamFlagged=0, tức admin đã tự sửa 2 cờ con trong lúc tài khoản vẫn
        // khóa, tạo trạng thái vô nghĩa). isActive là cấp cao nhất, 2 cờ con CHỈ có ý nghĩa khi
        // tài khoản đang mở — bắt admin mở khóa tài khoản trước mới cho sửa riêng lẻ.
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessRuleViolationException(
                    "Tài khoản này đang bị khóa — mở khóa tài khoản trước khi sửa cờ spam riêng lẻ.");
        }
        user.setIsSpamFlagged(req.flagged());
        user.setSpamFlagReason(req.flagged() ? req.reason() : null);
        user.setIsAiLocked(req.flagged());
        userRepository.save(user);
        if (req.flagged()) {
            notificationService.notify(userId, "SPAM_FLAGGED", "Tài khoản bị hạn chế",
                    "Tài khoản của bạn đã bị đánh dấu vi phạm chính sách và bị khóa tính năng AI. Lý do: " + req.reason(),
                    "/profile");
        } else {
            notificationService.notify(userId, "SPAM_UNFLAGGED", "Tài khoản đã được gỡ hạn chế",
                    "Tài khoản của bạn đã được bỏ đánh dấu vi phạm và mở lại quyền dùng AI.", "/profile");
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

    /**
     * Tiêu thụ AI theo người dùng (20/11/2026): khoảng ngày (mặc định 30 ngày gần nhất), tìm theo email/tên/ID, lọc đang bị khóa AI, lọc theo tính năng; phân trang
     * (tối đa 50/trang), sắp xếp theo chi phí giảm dần. Thay cho bản trả toàn bộ người dùng.
     */
    @GetMapping("/ai-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAiUsageSummary(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean lockedOnly,
            @RequestParam(required = false) String feature,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        java.time.LocalDate end = to == null ? java.time.LocalDate.now() : to;
        java.time.LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new com.lms.common.exception.InvalidRequestException("Khoảng ngày không hợp lệ: ngày kết thúc phải sau ngày bắt đầu, tối đa 366 ngày");
        }
        String kw = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        long qId = q != null && q.trim().matches("\\d{1,18}") ? Long.parseLong(q.trim()) : -1L;
        String feat = feature == null || feature.isBlank() ? null : feature.trim();
        int safeSize = Math.max(1, Math.min(size, 50));
        var result = aiUsageLogRepository.searchUsage(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), kw, qId, lockedOnly, feat,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), safeSize));
        return ResponseEntity.ok(Map.of("items", result.getContent(), "total", result.getTotalElements(), "page", result.getNumber(), "size", safeSize,
                "from", start.toString(), "to", end.toString()));
    }

    /** Các tính năng AI có dữ liệu — cho bộ lọc "Tính năng". */
    @GetMapping("/ai-usage/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getAiUsageFeatures() {
        return ResponseEntity.ok(aiUsageLogRepository.distinctFeatures());
    }

    /** Task 11A — tổng token/chi phí AI theo ngày (30 ngày gần nhất), phục vụ biểu đồ Admin Dashboard. */
    @GetMapping("/ai-usage/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAiUsageDaily(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) String feature) {
        java.time.LocalDate end = to == null ? java.time.LocalDate.now() : to;
        java.time.LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new com.lms.common.exception.InvalidRequestException("Khoảng ngày không hợp lệ: ngày kết thúc phải sau ngày bắt đầu, tối đa 366 ngày");
        }
        return ResponseEntity.ok(aiUsageLogRepository.getDailyUsageBetween(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                feature == null || feature.isBlank() ? null : feature.trim()));
    }

    @PutMapping("/users/{userId}/toggle-ai-lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> toggleAiLock(@PathVariable Long userId, @RequestParam boolean isLocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessRuleViolationException(
                    "Tài khoản này đang bị khóa — mở khóa tài khoản trước khi sửa cờ AI riêng lẻ.");
        }
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
