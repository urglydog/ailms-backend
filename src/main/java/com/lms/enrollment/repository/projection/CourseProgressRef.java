package com.lms.enrollment.repository.projection;

import java.time.LocalDateTime;

/** Một bản ghi tiến độ của học viên, gắn với khóa chứa bài học đó (lấy theo lô cho mọi khóa). */
public record CourseProgressRef(Long courseId, Long lessonId, Integer lastPositionSec, Boolean completed,
                                LocalDateTime updatedAt) {
}
