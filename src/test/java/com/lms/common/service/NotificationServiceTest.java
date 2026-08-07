package com.lms.common.service;

import com.lms.common.dto.NotificationDto.NotificationRes;
import com.lms.notification.entity.Notification;
import com.lms.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationService - UC08: Get Notifications
 * Tests retrieving notifications for authenticated users ordered by newest first
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testGetNotifications_ShouldReturnUserNotificationsOrderedByNewest() {
        Long userId = 1L;

        Notification n1 = new Notification();
        n1.setId(1L);
        n1.setType("DUBBING_COMPLETED");
        n1.setTitle("Dubbing Task 1");
        n1.setContent("Your dubbing task is completed");
        n1.setIsRead(false);
        LocalDateTime now = LocalDateTime.now();
        n1.setCreatedAt(now.minus(Duration.ofHours(1)));

        Notification n2 = new Notification();
        n2.setId(2L);
        n2.setType("DUBBING_FAILED");
        n2.setTitle("Dubbing Task 2");
        n2.setContent("Your dubbing task failed");
        n2.setIsRead(false);
        n2.setCreatedAt(now);

        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(Arrays.asList(n2, n1));

        List<NotificationRes> result = notificationService.getNotifications(userId);

        assertEquals(2, result.size());
        assertEquals("Dubbing Task 2", result.get(0).title());
        assertEquals("Dubbing Task 1", result.get(1).title());
    }

    @Test
    void testGetNotifications_ShouldReturnEmptyListIfNoNotifications() {
        Long userId = 2L;
        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());

        List<NotificationRes> result = notificationService.getNotifications(userId);

        assertEquals(0, result.size());
    }

    @Test
    void testGetNotifications_ShouldConvertNotificationToDto() {
        Long userId = 3L;

        Notification notification = new Notification();
        notification.setId(5L);
        notification.setType("COURSE_APPROVED");
        notification.setTitle("Course Approval");
        notification.setContent("Your course has been approved");
        notification.setLinkUrl("/courses/5");
        notification.setIsRead(true);
        notification.setCreatedAt(LocalDateTime.now());

        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(Arrays.asList(notification));

        List<NotificationRes> result = notificationService.getNotifications(userId);

        assertEquals(1, result.size());
        NotificationRes res = result.get(0);
        assertEquals(5L, res.id());
        assertEquals("COURSE_APPROVED", res.type());
        assertEquals("Course Approval", res.title());
        assertEquals("Your course has been approved", res.content());
        assertEquals("/courses/5", res.linkUrl());
        assertEquals(true, res.isRead());
    }
}
