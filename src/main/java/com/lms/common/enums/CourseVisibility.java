package com.lms.common.enums;

/**
 * "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — mặc định {@code PUBLIC}, giữ nguyên
 * hành vi hiện tại cho mọi khóa học đã có. Dùng bởi: Course.
 */
public enum CourseVisibility {
    /** Hiển thị trong tìm kiếm/danh mục công khai, ai cũng ghi danh được. */
    PUBLIC,
    /** Ẩn khỏi tìm kiếm — chỉ email đã được Giảng viên mời (bảng course_invites) mới xem/ghi danh được. */
    PRIVATE_INVITE,
    /** Ẩn khỏi tìm kiếm — ai có link cũng xem được trang chi tiết, nhưng cần đúng mật khẩu mới ghi danh được. */
    PRIVATE_PASSWORD
}
