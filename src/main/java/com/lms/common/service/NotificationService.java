package com.lms.common.service;

import com.lms.auth.repository.UserRepository;
import com.lms.common.dto.NotificationDto.NotificationRes;
import com.lms.notification.entity.Notification;
import com.lms.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<NotificationRes> getNotifications(Long userId) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * BR-NOTIFY-01 — mọi sự kiện phải VỪA lưu DB (người offline xem lại) VỪA bắn WebSocket
     * (người đang online thấy ngay). Dùng cho DUBBING_COMPLETED/DUBBING_FAILED (F5.3) và các
     * loại thông báo khác về sau — chỉ bắn WebSocket mà không lưu DB là bug.
     */
    @Transactional
    public void notify(Long userId, String type, String title, String content, String linkUrl) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setLinkUrl(linkUrl);
        Notification saved = notificationRepository.save(notification);

        messagingTemplate.convertAndSend("/topic/notifications/" + userId, convertToDto(saved));
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
