package com.lms.auth.controller;

import com.lms.auth.dto.StreakResponse;
import com.lms.auth.service.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/streak")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;

    @GetMapping("/me")
    public ResponseEntity<StreakResponse> getMyStreak(
            Principal principal,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(streakService.getStreakByEmail(principal.getName(), timezone));
    }
}
