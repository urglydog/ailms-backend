package com.lms.finance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lịch chi trả theo đợt (11/11/2026, Giai đoạn C): các ngày trong tháng (1–28) cấu hình ở {@code PAYOUT_DAYS_OF_MONTH}. */
public final class PayoutSchedule {

    private static final List<Integer> DEFAULT_DAYS = List.of(5, 20);

    private PayoutSchedule() {}

    /** Ngày chi trả kế tiếp, LUÔN sau {@code today} (yêu cầu gửi hôm nay thì vào đợt gần nhất sau hôm nay). */
    public static LocalDate nextPayoutDate(LocalDate today, String daysCsv) {
        List<Integer> days = parse(daysCsv);
        for (int d : days) {
            if (d > today.getDayOfMonth()) {
                return today.withDayOfMonth(d);
            }
        }
        return today.plusMonths(1).withDayOfMonth(days.get(0));
    }

    static List<Integer> parse(String csv) {
        List<Integer> result = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                try {
                    int d = Integer.parseInt(part.trim());
                    if (d >= 1 && d <= 28 && !result.contains(d)) {
                        result.add(d);
                    }
                } catch (NumberFormatException ignored) {
                    // bỏ qua phần tử rác, dùng các ngày hợp lệ còn lại
                }
            }
        }
        if (result.isEmpty()) {
            return DEFAULT_DAYS;
        }
        Collections.sort(result);
        return result;
    }
}
