package com.lms.material.service;

import com.lms.common.service.NotificationService;
import com.lms.material.repository.FlashcardReviewRepository;
import com.lms.material.repository.projection.FlashcardDueCount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Job nhắc ôn flashcard: đếm trong DB, lưu thông báo DB + WebSocket theo userId qua NotificationService. */
@ExtendWith(MockitoExtension.class)
class FlashcardNotificationJobTest {

    @Mock private FlashcardReviewRepository reviewRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private FlashcardNotificationJob job;

    @Test
    void notifySpacedRepetition_notifiesEachUserByIdWithDueCount() {
        when(reviewRepository.countDueByUser(any(LocalDateTime.class))).thenReturn(List.of(
                new FlashcardDueCount(1L, 3L), new FlashcardDueCount(2L, 1L)));

        job.notifySpacedRepetition();

        verify(notificationService).notify(eq(1L), eq("SRS_REMINDER"), any(String.class), contains("3 thẻ"), eq("/materials"));
        verify(notificationService).notify(eq(2L), eq("SRS_REMINDER"), any(String.class), contains("1 thẻ"), eq("/materials"));
        verify(reviewRepository, never()).findAll();
    }

    @Test
    void notifySpacedRepetition_noDueCards_sendsNothing() {
        when(reviewRepository.countDueByUser(any(LocalDateTime.class))).thenReturn(List.of());

        job.notifySpacedRepetition();

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void notifySpacedRepetition_oneUserFails_othersStillNotified() {
        when(reviewRepository.countDueByUser(any(LocalDateTime.class))).thenReturn(List.of(
                new FlashcardDueCount(1L, 3L), new FlashcardDueCount(2L, 1L)));
        doThrow(new RuntimeException("boom")).when(notificationService)
                .notify(eq(1L), any(), any(), any(), any());

        job.notifySpacedRepetition();

        verify(notificationService).notify(eq(2L), eq("SRS_REMINDER"), any(String.class), any(String.class), eq("/materials"));
    }
}
