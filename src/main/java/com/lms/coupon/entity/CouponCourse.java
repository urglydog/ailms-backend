package com.lms.coupon.entity;

import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng nối {@code Coupon}-{@code Course} (15/09/2026, mở rộng) — chỉ có dữ liệu khi
 * {@code Coupon.scopeType} là {@code SPECIFIC_COURSES} hoặc {@code SINGLE_COURSE} (xem
 * {@link com.lms.common.enums.CouponScopeType}).
 */
@Entity
@Table(name = "coupon_courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_courses_coupon_course", columnNames = {"coupon_id", "course_id"}))
@Getter
@Setter
public class CouponCourse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
