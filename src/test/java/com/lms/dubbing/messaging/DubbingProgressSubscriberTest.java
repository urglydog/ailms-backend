package com.lms.dubbing.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC20 — forward Redis Pub/Sub -> STOMP /topic/dubbing/{lessonId}. */
@ExtendWith(MockitoExtension.class)
class DubbingProgressSubscriberTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private Message message;

    private DubbingProgressSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new DubbingProgressSubscriber(messagingTemplate, objectMapper);
    }

    @Test
    void onMessage_forwardsChunkEventToLessonTopic() {
        String json = "{\"jobId\":1,\"lessonId\":21,\"chunkIndex\":0,\"totalChunks\":2,\"status\":\"COMPLETED\"}";
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/dubbing/21"), captor.capture());
        assertThat(captor.getValue().get("status").asText()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().has("chunkIndex")).isTrue();
    }

    @Test
    void onMessage_forwardsJobLevelEventWithoutChunkIndex() {
        String json = "{\"jobId\":1,\"lessonId\":21,\"status\":\"COMPLETED\"}";
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/dubbing/21"), captor.capture());
        assertThat(captor.getValue().has("chunkIndex")).isFalse();
    }

    @Test
    void onMessage_malformedJson_doesNotThrowOrForward() {
        when(message.getBody()).thenReturn("khong phai json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void onMessage_missingLessonId_doesNotThrowOrForward() {
        when(message.getBody()).thenReturn("{\"jobId\":1}".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
