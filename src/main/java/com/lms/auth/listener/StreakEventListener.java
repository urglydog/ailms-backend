package com.lms.auth.listener;

import com.lms.auth.service.StreakService;
import com.lms.enrollment.event.LessonCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StreakEventListener {

    private final StreakService streakService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLessonCompleted(LessonCompletedEvent event) {
        streakService.recordActivity(event.getUserId(), event.getCompletedAt());
    }
}
