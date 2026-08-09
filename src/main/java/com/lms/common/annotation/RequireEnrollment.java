package com.lms.common.annotation;

import java.lang.annotation.*;

/**
 * Annotation dùng để chặn quyền truy cập bài học/khóa học nếu học viên chưa mua (Ghi danh).
 * Yêu cầu method phải có tham số "lessonId" hoặc "courseId".
 * (Dùng kèm với EnrollmentAspect)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireEnrollment {
    /** 
     * Cho phép truy cập nếu bài học là Preview (is_preview = true).
     * Chỉ áp dụng khi check theo lessonId. 
     */
    boolean allowPreview() default false;
}
