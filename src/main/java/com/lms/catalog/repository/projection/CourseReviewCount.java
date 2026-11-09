package com.lms.catalog.repository.projection;

/** Số đánh giá đang hiển thị (chưa ẩn) của một khóa — đếm theo lô cho cả trang kết quả. */
public record CourseReviewCount(Long courseId, Long count) {
}
