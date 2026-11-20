package com.lms.invoice.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "line_no", nullable = false, updatable = false)
    private Integer lineNo;

    @Column(name = "description", nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;
}
