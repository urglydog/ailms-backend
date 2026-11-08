package com.lms.catalog.repository.projection;

/** Số bài và tổng thời lượng video (giây) của một khóa — tính theo lô cho cả trang kết quả. */
public record CourseLessonStats(Long courseId, Long lessonCount, Long totalDurationSec) {
}
