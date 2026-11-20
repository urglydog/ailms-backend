package com.lms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Hóa đơn thực tế của nhà cung cấp AI (Google AI Studio, Groq) cho một khoảng ngày, do admin nhập để đối soát với chi phí ước tính từ số token. Không xóa, không chồng lấn. */
@Entity
@Table(name = "ai_billing_entries")
@Getter
@Setter
public class AiBillingEntry extends BaseEntity {

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;
}
