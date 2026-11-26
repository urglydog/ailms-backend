package com.lms.enrollment.repository.projection;

/** Một bài học của một khóa, theo đúng thứ tự chương/bài — "Khóa học của tôi" lấy theo lô cho mọi khóa.
 * {@code lessonTitle}/{@code durationSec} (26/11/2026) cho hàng thẻ "Tiếp tục học" ở trang chủ. */
public record CourseLessonRef(Long courseId, Long lessonId, String lessonTitle, Integer durationSec) {

    public CourseLessonRef(Long courseId, Long lessonId) {
        this(courseId, lessonId, null, null);
    }
}
