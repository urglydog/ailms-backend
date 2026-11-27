package com.lms.community.controller;

import com.lms.common.config.StompAuthInterceptor;
import com.lms.community.dto.ChatMessageDto;
import com.lms.community.service.LessonChatService;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final LessonChatService chatService;

    /** (20/09/2026, sửa lỗi) — broadcast lại DTO đã LƯU (từ {@code saveMessage}), không còn echo
     * nguyên văn payload thô client gửi lên — id/timestamp/isInstructor phải là dữ liệu THẬT từ
     * DB, xem docblock {@link ChatMessageDto}. */
    @MessageMapping("/chat/{lessonId}")
    @SendTo("/topic/lesson/{lessonId}/chat")
    public ChatMessageDto handleChatMessage(
            @DestinationVariable Long lessonId, ChatMessageDto message, SimpMessageHeaderAccessor headers) {
        // (26/11/2026, lỗ hổng thật) senderId/senderName trong payload do client tự khai → ai cũng giả danh được người khác
        // (kể cả giảng viên). Danh tính phải lấy từ phiên đã xác thực JWT lúc CONNECT; payload chỉ đóng góp nội dung.
        Map<String, Object> session = headers.getSessionAttributes();
        Object uid = session == null ? null : session.get(StompAuthInterceptor.ATTR_USER_ID);
        if (!(uid instanceof Long userId)) {
            throw new MessageDeliveryException("Cần đăng nhập để gửi tin nhắn");
        }
        String name = chatService.displayNameOf(userId, message.senderName());
        return chatService.saveMessage(lessonId, userId, name, message.content(), message.parentId());
    }
}
