package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.common.exception.InvalidRequestException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Ma trận kiểm thử TOÀN BỘ khóa cấu hình sửa được trên giao diện: mỗi khóa phải nhận giá trị hợp lệ (biên dưới/trên) và từ chối mọi kiểu nhập sai phổ biến
 * (số âm, số thập phân, chữ, để trống, ký hiệu khoa học, dấu +, vượt giới hạn, tràn số).
 */
class ConfigValidationMatrixTest {

    /** Khóa số nguyên: {khóa, nhỏ nhất, lớn nhất}. */
    private static final Object[][] INT_KEYS = {
            {"REFUND_WINDOW_DAYS", 0, 90}, {"REFUND_MAX_PROGRESS_PCT", 0, 100}, {"PAYOUT_HOLD_EXTRA_DAYS", 0, 60},
            {"PAYOUT_MIN_AMOUNT", 0, 100_000_000}, {"DISPUTE_APPEAL_DAYS", 1, 90}, {"REFUND_FLAG_RATE_PCT", 1, 100},
            {"REFUND_FLAG_MIN_COUNT", 1, 1000}, {"DEBT_RESTRICT_DAYS", 1, 3650}, {"DEBT_SUSPEND_DAYS", 1, 3650},
            {"DEBT_MIN_REPAY", 1000, 100_000_000}, {"PAYOS_PAYOUT_MAX_AMOUNT", 0, 100_000_000}, {"DISPUTE_INSTRUCTOR_RESPONSE_HOURS", 1, 720},
            {"PAYOS_RETRY_COOLDOWN_MINUTES", 0, 1440}, {"PAYOS_MAX_ATTEMPTS", 1, 20},
            {"QUOTA_DUB_STUDENT", 0, 1000}, {"QUOTA_DUB_INSTRUCTOR", 0, 1000}, {"QUOTA_MATERIALS", 0, 1000},
            {"QUOTA_TUTOR", 0, 10_000}, {"QUOTA_GUEST_SEARCH", 0, 1000},
    };
    private static final String[] SWITCH_KEYS = {"PAYOS_PAYOUT_ENABLED", "REPORT_DAILY_ENABLED", "REPORT_WEEKLY_ENABLED", "REPORT_MONTHLY_ENABLED",
            "STATEMENT_EMAIL_INSTRUCTORS", "PUBLIC_INVOICE_LINK_ENABLED"};
    private static final String[] PCT_KEYS = {"REVENUE_ORGANIC_INSTRUCTOR_PCT", "REVENUE_REFERRAL_INSTRUCTOR_PCT"};
    private static final String[] BAD_NUMBERS = {"", "  ", "-1", "1.5", "abc", "1e3", "+5", "0x10", "99999999999", "5 5", "٣"};

    static Stream<Arguments> intKeysValidBounds() {
        return Stream.of(INT_KEYS).flatMap(k -> Stream.of(Arguments.of(k[0], String.valueOf(k[1])), Arguments.of(k[0], String.valueOf(k[2]))));
    }

    static Stream<Arguments> intKeysBadInputs() {
        return Stream.of(INT_KEYS).flatMap(k -> {
            Stream<Arguments> typos = Stream.of(BAD_NUMBERS).map(b -> Arguments.of(k[0], b));
            Stream<Arguments> over = Stream.of(Arguments.of(k[0], String.valueOf((int) k[2] + 1)));
            Stream<Arguments> under = (int) k[1] > 0 ? Stream.of(Arguments.of(k[0], String.valueOf((int) k[1] - 1))) : Stream.empty();
            return Stream.of(typos, over, under).flatMap(s -> s);
        });
    }

    static Stream<Arguments> switchBadInputs() {
        return Stream.of(SWITCH_KEYS).flatMap(k -> Stream.of("2", "-1", "true", "on", "", "0.5", "10").map(b -> Arguments.of(k, b)));
    }

    static Stream<Arguments> pctBadInputs() {
        return Stream.of(PCT_KEYS).flatMap(k -> Stream.of("", "-1", "101", "100.01", "abc", "1e2", "37.123", "+5", "٣", "1,5").map(b -> Arguments.of(k, b)));
    }

    @ParameterizedTest(name = "{0} nhận {1}")
    @MethodSource("intKeysValidBounds")
    void integerKeys_acceptTheirBounds(String key, String value) {
        assertThatCode(() -> FinanceConfigKeys.validate(key, value)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} từ chối \"{1}\"")
    @MethodSource("intKeysBadInputs")
    void integerKeys_rejectEveryKindOfBadInput(String key, String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate(key, value)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest(name = "{0} nhận 0 và 1")
    @MethodSource("switchKeys")
    void switchKeys_acceptOnlyZeroOrOne(String key) {
        assertThatCode(() -> FinanceConfigKeys.validate(key, "0")).doesNotThrowAnyException();
        assertThatCode(() -> FinanceConfigKeys.validate(key, "1")).doesNotThrowAnyException();
    }

    static Stream<String> switchKeys() {
        return Stream.of(SWITCH_KEYS);
    }

    @ParameterizedTest(name = "{0} từ chối \"{1}\"")
    @MethodSource("switchBadInputs")
    void switchKeys_rejectAnythingElse(String key, String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate(key, value)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest(name = "{0} từ chối \"{1}\"")
    @MethodSource("pctBadInputs")
    void percentKeys_rejectBadInputs(String key, String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate(key, value)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest(name = "{0} nhận 0, 37, 97.5, 100")
    @MethodSource("pctKeys")
    void percentKeys_acceptValidValues(String key) {
        for (String ok : new String[] {"0", "37", "97.5", "100", "99.99"}) {
            assertThatCode(() -> FinanceConfigKeys.validate(key, ok)).doesNotThrowAnyException();
        }
    }

    static Stream<String> pctKeys() {
        return Stream.of(PCT_KEYS);
    }

    @ParameterizedTest(name = "ngày chi trả \"{0}\" bị từ chối")
    @MethodSource("badPayoutDays")
    void payoutDays_rejectBadLists(String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("PAYOUT_DAYS_OF_MONTH", value)).isInstanceOf(InvalidRequestException.class);
    }

    static Stream<String> badPayoutDays() {
        return Stream.of("", "0", "29", "-5", "1.5", "abc", "5,5", "1,2,3,4,5", "5,,20", "5;20");
    }

    @ParameterizedTest(name = "ngày chi trả \"{0}\" hợp lệ")
    @MethodSource("goodPayoutDays")
    void payoutDays_acceptGoodLists(String value) {
        assertThatCode(() -> FinanceConfigKeys.validate("PAYOUT_DAYS_OF_MONTH", value)).doesNotThrowAnyException();
    }

    static Stream<String> goodPayoutDays() {
        return Stream.of("1", "28", "5,20", "1, 10, 20, 28");
    }

    @ParameterizedTest(name = "ngày lễ \"{0}\" bị từ chối")
    @MethodSource("badHolidays")
    void holidays_rejectBadLists(String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("REFUND_HOLIDAYS", value)).isInstanceOf(InvalidRequestException.class);
    }

    static Stream<String> badHolidays() {
        return Stream.of("2026-13-01", "2026-02-30", "25/12/2026", "abc", "2026-12-25,2026-12-25", "2026-12-25,,2027-01-01", "2026-12-25;2027-01-01");
    }

    @ParameterizedTest(name = "ngày lễ \"{0}\" hợp lệ")
    @MethodSource("goodHolidays")
    void holidays_acceptGoodLists(String value) {
        assertThatCode(() -> FinanceConfigKeys.validate("REFUND_HOLIDAYS", value)).doesNotThrowAnyException();
    }

    static Stream<String> goodHolidays() {
        return Stream.of("", "2026-12-25", "2026-12-25, 2027-01-01");
    }

    @ParameterizedTest(name = "email nhận báo cáo \"{0}\" bị từ chối")
    @MethodSource("badRecipients")
    void recipients_rejectBadLists(String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("REPORT_RECIPIENTS", value)).isInstanceOf(InvalidRequestException.class);
    }

    static Stream<String> badRecipients() {
        return Stream.of("abc", "a@b", "a@b.com;c@d.com", "a@b.com,,c@d.com", "a b@c.com", "@b.com");
    }

    @ParameterizedTest(name = "tên bên phát hành \"{0}\" bị từ chối")
    @MethodSource("blankStrings")
    void issuerName_rejectsBlank(String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("INVOICE_ISSUER_NAME", value)).isInstanceOf(InvalidRequestException.class);
    }

    static Stream<String> blankStrings() {
        return Stream.of("", "   ");
    }

    @org.junit.jupiter.api.Test
    void issuerNote_isCappedAndAnyKeyIsCappedAt255Characters() {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("INVOICE_ISSUER_NOTE", "x".repeat(201))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> FinanceConfigKeys.validate("INVOICE_ISSUER_NAME", "x".repeat(256))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> FinanceConfigKeys.validate("PAYOS_PAYOUT_ENABLED", null)).isInstanceOf(InvalidRequestException.class);
    }
}
