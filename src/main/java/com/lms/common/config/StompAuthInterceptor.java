package com.lms.common.config;

import com.lms.auth.security.JwtTokenProvider;
import com.lms.communication.repository.ConversationRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Hiệu năng đợt 5 — chat chuyển từ polling sang WebSocket nên topic riêng tư phải được kiểm quyền:
 * <ul>
 *   <li>CONNECT: đọc JWT từ header {@code Authorization}; hợp lệ thì gắn userId + email vào phiên.
 *       Không có token vẫn kết nối được (topic công khai như chat bài học).</li>
 *   <li>SUBSCRIBE {@code /topic/notifications/{x}}: chỉ chính chủ ({@code x} = id hoặc email).</li>
 *   <li>SUBSCRIBE {@code /topic/conversations/{id}}: chỉ 2 người trong hội thoại.</li>
 * </ul>
 * Trước đây ai biết id đều nghe được tin nhắn riêng/thông báo của người khác.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String ATTR_USER_ID = "lms.userId";
    static final String ATTR_EMAIL = "lms.email";
    private static final String NOTIFY_PREFIX = "/topic/notifications/";
    private static final String CONVERSATION_PREFIX = "/topic/conversations/";

    private final JwtTokenProvider jwtTokenProvider;
    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        Map<String, Object> session = accessor.getSessionAttributes();

        if (accessor.getCommand() == StompCommand.CONNECT && session != null) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    session.put(ATTR_USER_ID, jwtTokenProvider.getUserId(token));
                    session.put(ATTR_EMAIL, jwtTokenProvider.getAuthentication(token).getName());
                }
            }
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String dest = accessor.getDestination();
            if (dest == null) return message;
            Long userId = session == null ? null : (Long) session.get(ATTR_USER_ID);
            String email = session == null ? null : (String) session.get(ATTR_EMAIL);
            if (dest.startsWith(NOTIFY_PREFIX)) {
                String owner = dest.substring(NOTIFY_PREFIX.length());
                boolean self = userId != null && (owner.equals(String.valueOf(userId)) || owner.equals(email));
                if (!self) throw new MessageDeliveryException("Không có quyền nghe thông báo này");
            } else if (dest.startsWith(CONVERSATION_PREFIX)) {
                Long conversationId = parseId(dest.substring(CONVERSATION_PREFIX.length()));
                if (userId == null || conversationId == null
                        || !conversationRepository.isParticipant(conversationId, userId)) {
                    throw new MessageDeliveryException("Không có quyền nghe hội thoại này");
                }
            }
        }
        return message;
    }

    private static Long parseId(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
