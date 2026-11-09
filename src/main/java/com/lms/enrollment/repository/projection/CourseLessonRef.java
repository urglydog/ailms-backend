package com.lms.enrollment.repository.projection;

/** Một bài học của một khóa, theo đúng thứ tự chương/bài — "Khóa học của tôi" lấy theo lô cho mọi khóa. */
public record CourseLessonRef(Long courseId, Long lessonId) {
}
