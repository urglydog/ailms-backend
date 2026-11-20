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
        return notificationRepository.findTop200ByUser_IdAndHiddenAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** (26/09/2026, sửa lỗi) — persist "đã đọc" xuống DB, xem docblock
     * {@code NotificationRepository.markAllAsRead}. */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        notificationRepository.markAsRead(notificationId, userId);
    }

    /**
     * BR-NOTIFY-01 — mọi sự kiện phải VỪA lưu DB (người offline xem lại) VỪA bắn WebSocket
     * (người đang online thấy ngay). Dùng cho DUBBING_COMPLETED/DUBBING_FAILED (F5.3) và các
     * loại thông báo khác về sau — chỉ bắn WebSocket mà không lưu DB là bug.
     */
    @Transactional
    public void notify(Long userId, String type, String title, String content, String linkUrl) {
        notify(userId, type, title, content, linkUrl, null);
    }

    /** Như {@link #notify(Long, String, String, String, String)} nhưng gắn nguồn (vd {@code ANN:12}) để sau này sửa/thu hồi hàng loạt. */
    @Transactional
    public void notify(Long userId, String type, String title, String content, String linkUrl, String sourceRef) {
        Notification notification = new Notification();
        notification.setSourceRef(sourceRef);
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setLinkUrl(linkUrl);
        Notification saved = notificationRepository.save(notification);

        messagingTemplate.convertAndSend("/topic/notifications/" + userId, convertToDto(saved));
    }

    /** Sửa tiêu đề/nội dung của các thông báo đã gửi từ một nguồn (vd thông báo hệ thống được sửa). Trả số thông báo đã cập nhật. */
    @Transactional
    public int updateBySource(String sourceRef, String title, String content) {
        return notificationRepository.updateBySource(sourceRef, title, content);
    }

    /** Thu hồi: ẩn các thông báo của một nguồn khỏi người dùng (không xóa). Trả số thông báo bị ẩn. */
    @Transactional
    public int hideBySource(String sourceRef) {
        return notificationRepository.hideBySource(sourceRef, java.time.LocalDateTime.now());
    }

    /** Khôi phục: hiện lại các thông báo đã thu hồi của một nguồn. Trả số thông báo được hiện lại. */
    @Transactional
    public int unhideBySource(String sourceRef) {
        return notificationRepository.unhideBySource(sourceRef);
    }

    /** Xóa hẳn các thông báo của một nguồn. Trả số thông báo đã xóa. */
    @Transactional
    public int deleteBySource(String sourceRef) {
        return notificationRepository.deleteBySource(sourceRef);
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
