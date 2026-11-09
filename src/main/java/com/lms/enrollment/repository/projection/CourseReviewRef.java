package com.lms.enrollment.repository.projection;

/** Đánh giá của học viên cho một khóa (lấy theo lô). */
public record CourseReviewRef(Long courseId, Integer rating) {
}
