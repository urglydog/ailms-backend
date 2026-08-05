package com.lms.common.controller;

import com.lms.common.dto.NotificationDto.NotificationRes;
import com.lms.common.service.NotificationService;
import com.lms.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * REST Controller for Notification operations - UC08: Get Notifications
 * Endpoints for retrieving notifications for authenticated users
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * UC08: Get notifications for the authenticated user
     * Returns all notifications for the current user ordered by newest first
     *
     * @param principal the authenticated user's principal
     * @return list of notification responses ordered by creation date (DESC)
     */
    @GetMapping
    public ResponseEntity<List<NotificationRes>> getNotifications(Principal principal) {
        Long userId = userService.getUserByEmail(principal.getName()).id();
        List<NotificationRes> notifications = notificationService.getNotifications(userId);
        return ResponseEntity.ok(notifications);
    }
}
