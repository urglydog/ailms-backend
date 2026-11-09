package com.lms.material.service;

import com.lms.material.repository.FlashcardReviewRepository;
import com.lms.material.repository.projection.FlashcardDueCount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hiệu năng đợt 3: job nhắc ôn flashcard đếm thẻ đến hạn ngay trong DB, không nạp toàn bộ bảng. */
@ExtendWith(MockitoExtension.class)
class FlashcardNotificationJobTest {

    @Mock private FlashcardReviewRepository reviewRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private FlashcardNotificationJob job;

    @Test
    @SuppressWarnings("unchecked")
    void notifySpacedRepetition_sendsOneMessagePerUserWithDueCount() {
        when(reviewRepository.countDueByUser(any(LocalDateTime.class))).thenReturn(List.of(
                new FlashcardDueCount("a@lms.local", 3L), new FlashcardDueCount("b@lms.local", 1L)));

        job.notifySpacedRepetition();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/a@lms.local"), payload.capture());
        assertThat(((Map<String, Object>) payload.getValue()).get("dueCount")).isEqualTo(3L);
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/b@lms.local"), any(Object.class));
        verify(messagingTemplate, times(2)).convertAndSend(any(String.class), any(Object.class));
        verify(reviewRepository, never()).findAll();
    }

    @Test
    void notifySpacedRepetition_noDueCards_sendsNothing() {
        when(reviewRepository.countDueByUser(any(LocalDateTime.class))).thenReturn(List.of());

        job.notifySpacedRepetition();

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
