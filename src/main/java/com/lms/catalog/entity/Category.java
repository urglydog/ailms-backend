package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Danh mục môn học (UC43).
 *
 * <p>BR-COURSE-05: <b>chỉ Admin</b> được tạo/sửa/xoá; Giảng viên chỉ chọn từ danh
 * mục có sẵn. Không xoá được danh mục còn khóa học tham chiếu.
 *
 * <p><b>Thiết kế có chủ đích:</b> danh mục là cấu trúc <b>phẳng</b>, không phân cấp.
 * Nếu sau này cần đa cấp thì thêm {@code parentId} (self-reference) — hiện BR-COURSE-05
 * không yêu cầu nên giữ đơn giản.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Dùng cho URL thân thiện. */
    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;
}
