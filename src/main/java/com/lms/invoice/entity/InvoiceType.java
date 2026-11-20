package com.lms.invoice.entity;

/** Loại chứng từ; {@link #prefix} là tiền tố số chứng từ (vd RCP-202611-000001). */
public enum InvoiceType {
    RECEIPT("RCP"),
    REFUND("RFD"),
    PAYOUT("PAY"),
    DEBT_RECEIPT("DBT");

    private final String prefix;

    InvoiceType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
