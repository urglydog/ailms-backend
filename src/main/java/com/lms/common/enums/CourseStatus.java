package com.lms.common.enums;

/**
 * Vòng đời khóa học (BR-COURSE-01..04).
 * DRAFT -> PENDING -> PUBLISHED | REJECTED -> ARCHIVED
 * REJECTED quay về DRAFT, tối đa 5 lần gửi duyệt lại.
 * Khóa đã có học viên chỉ được xoá mềm sang ARCHIVED (BR-COURSE-03).
 *
 * <p>Dùng bởi: Course</p>
 */
public enum CourseStatus {
    DRAFT,
    PENDING,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}
