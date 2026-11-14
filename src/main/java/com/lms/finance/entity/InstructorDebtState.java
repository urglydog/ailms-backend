package com.lms.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Giảng viên đang nợ (V166): chỉ tồn tại dòng khi số dư khả dụng âm. {@code debtSince} = lúc bắt đầu nợ liên tục. */
@Entity
@Table(name = "instructor_debt_state")
@Getter
@Setter
public class InstructorDebtState {

    @Id
    @Column(name = "instructor_id")
    private Long instructorId;

    @Column(name = "debt_since", nullable = false)
    private LocalDateTime debtSince;

    /** Bậc cao nhất đã thông báo (1 = đang nợ, 2 = hạn chế, 3 = ngừng bán). */
    @Column(name = "last_level", nullable = false)
    private Integer lastLevel = 1;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
