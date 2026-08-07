package com.lms.common.service;

import com.lms.common.dto.NotificationDto.NotificationRes;
import com.lms.notification.entity.Notification;
import com.lms.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationRes> getNotifications(Long userId) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private NotificationRes convertToDto(Notification n) {
        return new NotificationRes(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.getLinkUrl(),
                n.getIsRead(),
                n.getCreatedAt()
        );
    }
}
