package com.lms.common.service;

import com.lms.common.entity.SystemConfig;
import com.lms.common.repository.SystemConfigRepository;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.service.FinanceAuditService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấu hình hệ thống key-value. (11/11/2026, Epic hoàn tiền – chi trả) bổ sung: đọc giá trị có
 * kiểu ({@link #getInt}, {@link #getDecimal}, {@link #getString}) qua cache trong bộ nhớ 60 giây,
 * kiểm tra hợp lệ khóa tài chính trước khi lưu, và ghi nhật ký kiểm toán mỗi lần đổi.
 * Đọc config lỗi/thiếu luôn trả giá trị mặc định do người gọi truyền vào — luồng thanh toán
 * không được vỡ vì cấu hình.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final SystemConfigRepository systemConfigRepository;
    private final FinanceAuditService financeAuditService;

    private volatile Map<String, String> cache = Map.of();
    private volatile long loadedAtMs = 0L;

    @Transactional(readOnly = true)
    public List<SystemConfig> getAll() {
        return systemConfigRepository.findAll();
    }

    /**
     * Map {@code configKey -> configValue mới}; bỏ qua key không tồn tại sẵn (không tự tạo mới).
     * Khóa tài chính được kiểm tra hợp lệ — 1 giá trị sai thì CẢ lần cập nhật bị từ chối (không
     * lưu dở). Mỗi khóa thực sự đổi giá trị được ghi 1 dòng nhật ký.
     */
    @Transactional
    public List<SystemConfig> updateValues(Map<String, String> updates, String actorEmail) {
        Map<String, String> safeUpdates = updates == null ? Map.of() : updates;

        // Bước 1: kiểm tra toàn bộ trước khi sửa bất kỳ giá trị nào.
        safeUpdates.forEach((key, value) -> {
            if (systemConfigRepository.findByConfigKey(key).isPresent()) {
                FinanceConfigKeys.validate(key, value);
            }
        });

        // Bước 2: áp dụng + ghi nhật ký.
        safeUpdates.forEach((key, value) ->
                systemConfigRepository.findByConfigKey(key).ifPresent(cfg -> {
                    String before = cfg.getConfigValue();
                    String after = value.trim();
                    if (!after.equals(before)) {
                        cfg.setConfigValue(after);
                        financeAuditService.record(actorEmail, "CONFIG_UPDATED", "system_config", key,
                                Map.of("value", before), Map.of("value", after));
                    }
                }));
        invalidateCache();
        return systemConfigRepository.findAll();
    }

    public int getInt(String key, int defaultValue) {
        String raw = rawValue(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Cấu hình {} = \"{}\" không phải số nguyên, dùng mặc định {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String raw = rawValue(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Cấu hình {} = \"{}\" không phải số, dùng mặc định {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    public String getString(String key, String defaultValue) {
        String raw = rawValue(key);
        return raw == null ? defaultValue : raw;
    }

    private String rawValue(String key) {
        try {
            return snapshot().get(key);
        } catch (RuntimeException e) {
            // DB lỗi tạm: dùng bản cache cũ (nếu có), không làm vỡ luồng gọi.
            log.warn("Không đọc được system_configs ({}), dùng cache cũ/mặc định", e.getMessage());
            return cache.get(key);
        }
    }

    private Map<String, String> snapshot() {
        long now = System.currentTimeMillis();
        if (now - loadedAtMs < CACHE_TTL_MS) {
            return cache;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - loadedAtMs < CACHE_TTL_MS) {
                return cache;
            }
            Map<String, String> fresh = new HashMap<>();
            for (SystemConfig c : systemConfigRepository.findAll()) {
                fresh.put(c.getConfigKey(), c.getConfigValue());
            }
            cache = new LinkedHashMap<>(fresh);
            loadedAtMs = System.currentTimeMillis();
            return cache;
        }
    }

    private void invalidateCache() {
        loadedAtMs = 0L;
    }
}
