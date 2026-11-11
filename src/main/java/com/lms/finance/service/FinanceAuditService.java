package com.lms.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.repository.UserRepository;
import com.lms.common.ratelimit.RateLimitFilter;
import com.lms.finance.entity.FinanceAuditLog;
import com.lms.finance.repository.FinanceAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi nhật ký kiểm toán tài chính (11/11/2026, Epic hoàn tiền – chi trả). Chạy TRONG transaction
 * của người gọi ({@code REQUIRED}) → thay đổi tiền và dòng nhật ký của nó cùng commit hoặc cùng
 * rollback, không bao giờ lệch nhau. Không bao giờ ghi số tài khoản ngân hàng/token vào
 * {@code before}/{@code after} — chỉ truyền dữ liệu đã che.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceAuditService {

    private static final int MAX_JSON_LENGTH = 8000;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final FinanceAuditLogRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param actorEmail email người thực hiện; {@code null} nếu là hệ thống (job, webhook)
     * @param before     trạng thái trước (Map/record/String...), có thể null
     * @param after      trạng thái sau, có thể null
     */
    @Transactional
    public void record(String actorEmail, String action, String entityType, String entityId,
                       Object before, Object after) {
        FinanceAuditLog entry = new FinanceAuditLog();
        entry.setActorRole("SYSTEM");
        if (actorEmail != null) {
            entry.setActorEmail(actorEmail);
            userRepository.findByEmail(actorEmail).ifPresent(u -> {
                entry.setActorUserId(u.getId());
                entry.setActorRole(u.getRole().name());
            });
        }
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeJson(toJson(before));
        entry.setAfterJson(toJson(after));

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            entry.setIp(truncate(RateLimitFilter.clientIp(request), 45));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
        }
        repository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), MAX_JSON_LENGTH);
        } catch (JsonProcessingException e) {
            log.warn("Không serialize được dữ liệu nhật ký tài chính: {}", e.getMessage());
            return "\"<unserializable>\"";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
