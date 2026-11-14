package com.lms.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Đã cảnh báo admin về tỷ lệ hoàn tiền cao của khóa này lúc nào (V167) — tránh nhắc lặp mỗi đêm. */
@Entity
@Table(name = "refund_rate_alerts")
@Getter
@Setter
public class RefundRateAlert {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "alerted_at", nullable = false)
    private LocalDateTime alertedAt;
}
