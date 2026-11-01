package com.lms.common.service;

import com.lms.common.entity.SystemConfig;
import com.lms.common.repository.SystemConfigRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Transactional(readOnly = true)
    public List<SystemConfig> getAll() {
        return systemConfigRepository.findAll();
    }

    /** Map {@code configKey -> configValue mới}; bỏ qua key không tồn tại sẵn (không tự tạo mới). */
    @Transactional
    public List<SystemConfig> updateValues(Map<String, String> updates) {
        updates.forEach((key, value) ->
                systemConfigRepository.findByConfigKey(key).ifPresent(cfg -> cfg.setConfigValue(value)));
        return systemConfigRepository.findAll();
    }
}
