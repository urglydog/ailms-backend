package com.lms.common.config;

import com.lms.auth.security.JwtTokenProvider;
import com.lms.communication.repository.ConversationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Hiệu năng đợt 5: topic WebSocket riêng tư chỉ người có quyền mới subscribe được. */
@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    @Mock private JwtTokenProvider jwt;
    @Mock private ConversationRepository conversations;
    @InjectMocks private StompAuthInterceptor interceptor;

    private static Message<byte[]> frame(StompCommand cmd, Map<String, Object> session, String dest, String auth) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        acc.setSessionAttributes(session);
        if (dest != null) acc.setDestination(dest);
        if (auth != null) acc.setNativeHeader("Authorization", auth);
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private Map<String, Object> connectedAs(long userId, String email) {
        Map<String, Object> session = new HashMap<>();
        when(jwt.validateToken("tok")).thenReturn(true);
        when(jwt.getUserId("tok")).thenReturn(userId);
        when(jwt.getAuthentication("tok")).thenReturn(new UsernamePasswordAuthenticationToken(email, "tok", List.of()));
        interceptor.preSend(frame(StompCommand.CONNECT, session, null, "Bearer tok"), null);
        return session;
    }

    @Test
    void connect_withValidToken_storesUserInSession() {
        Map<String, Object> session = connectedAs(7L, "a@lms.local");
        assertThat(session).containsEntry(StompAuthInterceptor.ATTR_USER_ID, 7L);
    }

    @Test
    void ownNotifications_allowed_othersRejected() {
        Map<String, Object> session = connectedAs(7L, "a@lms.local");
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/notifications/7", null), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/notifications/8", null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void conversation_onlyParticipants() {
        Map<String, Object> session = connectedAs(7L, "a@lms.local");
        when(conversations.isParticipant(1L, 7L)).thenReturn(true);
        when(conversations.isParticipant(2L, 7L)).thenReturn(false);

        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/conversations/1", null), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/conversations/2", null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void anonymous_canUsePublicTopics_butNotPrivate() {
        Map<String, Object> session = new HashMap<>();
        interceptor.preSend(frame(StompCommand.CONNECT, session, null, null), null);

        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/lesson/3/chat", null), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, session, "/topic/conversations/1", null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
