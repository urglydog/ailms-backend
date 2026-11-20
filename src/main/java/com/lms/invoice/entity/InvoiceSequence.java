package com.lms.invoice.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Bộ đếm số chứng từ theo khóa {@code LOẠI-YYYYMM}; luôn đọc với khóa ghi (PESSIMISTIC_WRITE) khi cấp số. */
@Entity
@Table(name = "invoice_sequences")
@Getter
@Setter
public class InvoiceSequence extends BaseEntity {

    @Column(name = "seq_key", nullable = false, unique = true, length = 20)
    private String seqKey;

    @Column(name = "seq_value", nullable = false)
    private Long seqValue = 0L;
}
