package com.lms.finance;

import com.lms.common.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Tên khóa + giá trị mặc định + kiểm tra hợp lệ của cấu hình tài chính trong {@code system_configs}
 * (11/11/2026, Epic hoàn tiền – chi trả). Khóa được seed ở V156; mặc định ở đây TRÙNG với seed và
 * là giá trị dự phòng khi đọc config lỗi/thiếu — để luồng thanh toán không bao giờ vỡ chỉ vì
 * cấu hình hỏng.
 */
public final class FinanceConfigKeys {

    public static final String REVENUE_ORGANIC_INSTRUCTOR_PCT = "REVENUE_ORGANIC_INSTRUCTOR_PCT";
    public static final String REVENUE_REFERRAL_INSTRUCTOR_PCT = "REVENUE_REFERRAL_INSTRUCTOR_PCT";
    public static final String REFUND_WINDOW_DAYS = "REFUND_WINDOW_DAYS";
    public static final String REFUND_MAX_PROGRESS_PCT = "REFUND_MAX_PROGRESS_PCT";
    public static final String REFUND_HOLIDAYS = "REFUND_HOLIDAYS";
    public static final String PAYOUT_HOLD_EXTRA_DAYS = "PAYOUT_HOLD_EXTRA_DAYS";
    public static final String PAYOUT_MIN_AMOUNT = "PAYOUT_MIN_AMOUNT";
    public static final String PAYOUT_DAYS_OF_MONTH = "PAYOUT_DAYS_OF_MONTH";
    public static final String DISPUTE_INSTRUCTOR_RESPONSE_HOURS = "DISPUTE_INSTRUCTOR_RESPONSE_HOURS";

    public static final BigDecimal DEFAULT_ORGANIC_INSTRUCTOR_PCT = new BigDecimal("37");
    public static final BigDecimal DEFAULT_REFERRAL_INSTRUCTOR_PCT = new BigDecimal("97");
    public static final int DEFAULT_REFUND_WINDOW_DAYS = 7;
    public static final int DEFAULT_PAYOUT_HOLD_EXTRA_DAYS = 3;

    private static final Set<String> PCT_KEYS =
            Set.of(REVENUE_ORGANIC_INSTRUCTOR_PCT, REVENUE_REFERRAL_INSTRUCTOR_PCT);

    private FinanceConfigKeys() {}

    /** Kiểm tra giá trị trước khi lưu. Khóa không thuộc nhóm tài chính (vd QUOTA_*) bỏ qua. */
    public static void validate(String key, String value) {
        if (value == null) {
            throw invalid(key, "không được để trống");
        }
        if (value.length() > 255) {
            throw invalid(key, "tối đa 255 ký tự");
        }
        String v = value.trim();
        if (PCT_KEYS.contains(key)) {
            BigDecimal pct = parseDecimal(key, v);
            if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
                throw invalid(key, "phải trong khoảng 0–100");
            }
        } else if (REFUND_WINDOW_DAYS.equals(key)) {
            requireIntRange(key, v, 0, 90);
        } else if (REFUND_MAX_PROGRESS_PCT.equals(key)) {
            requireIntRange(key, v, 0, 100);
        } else if (PAYOUT_HOLD_EXTRA_DAYS.equals(key)) {
            requireIntRange(key, v, 0, 60);
        } else if (PAYOUT_MIN_AMOUNT.equals(key)) {
            requireIntRange(key, v, 0, 100_000_000);
        } else if (DISPUTE_INSTRUCTOR_RESPONSE_HOURS.equals(key)) {
            requireIntRange(key, v, 1, 720);
        } else if (PAYOUT_DAYS_OF_MONTH.equals(key)) {
            validateDaysOfMonth(key, v);
        } else if (REFUND_HOLIDAYS.equals(key)) {
            validateHolidays(key, v);
        }
    }

    private static void validateDaysOfMonth(String key, String v) {
        if (v.isEmpty()) {
            throw invalid(key, "cần ít nhất 1 ngày (1–28)");
        }
        String[] parts = v.split(",");
        if (parts.length > 4) {
            throw invalid(key, "tối đa 4 ngày chi trả mỗi tháng");
        }
        for (String p : parts) {
            requireIntRange(key, p.trim(), 1, 28);
        }
    }

    private static void validateHolidays(String key, String v) {
        if (v.isEmpty()) {
            return;
        }
        for (String p : v.split(",")) {
            try {
                LocalDate.parse(p.trim());
            } catch (DateTimeParseException e) {
                throw invalid(key, "ngày \"" + p.trim() + "\" không đúng dạng YYYY-MM-DD");
            }
        }
    }

    private static BigDecimal parseDecimal(String key, String v) {
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw invalid(key, "phải là số");
        }
    }

    private static void requireIntRange(String key, String v, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw invalid(key, "phải là số nguyên");
        }
        if (n < min || n > max) {
            throw invalid(key, "phải trong khoảng " + min + "–" + max);
        }
    }

    private static InvalidRequestException invalid(String key, String reason) {
        return new InvalidRequestException("Cấu hình " + key + " không hợp lệ: " + reason);
    }
}
