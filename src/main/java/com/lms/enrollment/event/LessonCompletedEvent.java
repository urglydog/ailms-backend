package com.lms.enrollment.event;

import org.springframework.context.ApplicationEvent;
import java.time.Instant;

public class LessonCompletedEvent extends ApplicationEvent {
    private final Long userId;
    private final Instant completedAt;

    public LessonCompletedEvent(Object source, Long userId, Instant completedAt) {
        super(source);
        this.userId = userId;
        this.completedAt = completedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
