package com.lms.community.controller;

import com.lms.common.config.StompAuthInterceptor;
import com.lms.community.dto.ChatMessageDto;
import com.lms.community.service.LessonChatService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 26/11/2026 — danh tính người gửi chat phải lấy từ phiên đã xác thực, không tin payload client. */
@ExtendWith(MockitoExtension.class)
class WebSocketChatControllerTest {

    @Mock private LessonChatService chatService;
    @InjectMocks private WebSocketChatController controller;

    private static SimpMessageHeaderAccessor headers(Map<String, Object> session) {
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.create();
        acc.setSessionAttributes(session);
        return acc;
    }

    private static ChatMessageDto payload(String senderId, String senderName) {
        return new ChatMessageDto("x", senderId, senderName, null, "xin chào", "2026-11-26T00:00:00", null, false);
    }

    @Test
    void usesAuthenticatedUser_notSpoofedPayload() {
        Map<String, Object> session = new HashMap<>();
        session.put(StompAuthInterceptor.ATTR_USER_ID, 7L);
        ChatMessageDto saved = payload("7", "Học viên Thật");
        when(chatService.displayNameOf(7L, "Giảng viên giả")).thenReturn("Học viên Thật");
        when(chatService.saveMessage(5L, 7L, "Học viên Thật", "xin chào", null)).thenReturn(saved);

        // client khai senderId = 99 (người khác) và tên giả — phải bị bỏ qua
        ChatMessageDto out = controller.handleChatMessage(5L, payload("99", "Giảng viên giả"), headers(session));

        assertThat(out).isSameAs(saved);
        verify(chatService).saveMessage(eq(5L), eq(7L), eq("Học viên Thật"), eq("xin chào"), eq(null));
        verify(chatService, never()).saveMessage(any(), eq(99L), any(), any(), any());
    }

    @Test
    void rejectsWhenSessionNotAuthenticated() {
        assertThatThrownBy(() -> controller.handleChatMessage(5L, payload("99", "ai đó"), headers(new HashMap<>())))
                .isInstanceOf(MessageDeliveryException.class);
        verify(chatService, never()).saveMessage(any(), any(), any(), any(), any());
    }
}
