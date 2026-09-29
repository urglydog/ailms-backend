package com.lms.bundle.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "course_bundles")
@Getter
@Setter
public class CourseBundle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // @OrderBy bắt buộc: thứ tự khóa học trong bundle quyết định khóa nào "hấp thụ" phần dư làm
    // tròn khi tính pro-rated (xem PaymentService.createBatchPayment) — không có @OrderBy, thứ
    // tự Hibernate trả về không xác định giữa các lần fetch, gây lệch giá vài đồng giữa lúc FE
    // preview và lúc BE chốt thanh toán.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_bundle_items",
            joinColumns = @JoinColumn(name = "bundle_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @OrderBy("id ASC")
    private List<Course> courses = new ArrayList<>();
}
