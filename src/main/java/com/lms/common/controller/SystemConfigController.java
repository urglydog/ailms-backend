package com.lms.common.controller;

import com.lms.common.entity.SystemConfig;
import com.lms.common.service.SystemConfigService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public record ConfigRes(String key, String value, String description) {
        static ConfigRes from(SystemConfig c) {
            return new ConfigRes(c.getConfigKey(), c.getConfigValue(), c.getDescription());
        }
    }

    public record UpdateReq(Map<String, String> values) {}

    @GetMapping
    public ResponseEntity<List<ConfigRes>> getAll() {
        return ResponseEntity.ok(systemConfigService.getAll().stream().map(ConfigRes::from).toList());
    }

    @PutMapping
    public ResponseEntity<List<ConfigRes>> update(@RequestBody UpdateReq req) {
        return ResponseEntity.ok(systemConfigService.updateValues(req.values()).stream().map(ConfigRes::from).toList());
    }
}
