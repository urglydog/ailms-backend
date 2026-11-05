package com.lms.common.enums;

/**
 * Nhóm vấn đề của ticket hỗ trợ (05/11/2026) — khớp các nhóm ở trang Trợ giúp {@code /help} (FE).
 * {@link #code} (3 ký tự) nằm trong mã ticket hiển thị, vd {@code SUP-251105-ACC-00002}.
 *
 * <p>Dùng bởi: SupportTicket</p>
 */
public enum SupportTicketCategory {
    ACCOUNT("ACC"),
    PAYMENT("PAY"),
    COURSE("CRS"),
    LEARNING("LRN"),
    AI_FEATURES("AIF"),
    EXAM("EXM"),
    CERTIFICATE("CRT"),
    COMMUNITY("COM"),
    LIVE("LIV"),
    INSTRUCTOR("INS"),
    PRIVACY("PRV"),
    TECHNICAL("TEC"),
    OTHER("OTH");

    private final String code;

    SupportTicketCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
