package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FinanceConfigKeysTest {

    @ParameterizedTest
    @CsvSource({
            "REVENUE_ORGANIC_INSTRUCTOR_PCT,37",
            "REVENUE_ORGANIC_INSTRUCTOR_PCT,0",
            "REVENUE_REFERRAL_INSTRUCTOR_PCT,100",
            "REVENUE_REFERRAL_INSTRUCTOR_PCT,97.5",
            "REFUND_WINDOW_DAYS,7",
            "REFUND_MAX_PROGRESS_PCT,20",
            "PAYOUT_HOLD_EXTRA_DAYS,3",
            "PAYOUT_MIN_AMOUNT,100000",
            "DISPUTE_INSTRUCTOR_RESPONSE_HOURS,48",
            "PAYOUT_DAYS_OF_MONTH,'5,20'",
            "PAYOUT_DAYS_OF_MONTH,28"
    })
    void validValues_areAccepted(String key, String value) {
        assertThatCode(() -> FinanceConfigKeys.validate(key, value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "REVENUE_ORGANIC_INSTRUCTOR_PCT,-1",
            "REVENUE_ORGANIC_INSTRUCTOR_PCT,101",
            "REVENUE_ORGANIC_INSTRUCTOR_PCT,abc",
            "REVENUE_REFERRAL_INSTRUCTOR_PCT,1e3",
            "REFUND_WINDOW_DAYS,91",
            "REFUND_WINDOW_DAYS,-1",
            "REFUND_WINDOW_DAYS,7.5",
            "REFUND_MAX_PROGRESS_PCT,101",
            "PAYOUT_HOLD_EXTRA_DAYS,61",
            "PAYOUT_MIN_AMOUNT,-5",
            "DISPUTE_INSTRUCTOR_RESPONSE_HOURS,0",
            "PAYOUT_DAYS_OF_MONTH,29",
            "PAYOUT_DAYS_OF_MONTH,'1,2,3,4,5'",
            "PAYOUT_DAYS_OF_MONTH,'5,x'"
    })
    void invalidValues_areRejected(String key, String value) {
        assertThatThrownBy(() -> FinanceConfigKeys.validate(key, value))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void holidays_acceptEmptyAndIsoDates_rejectGarbage() {
        assertThatCode(() -> FinanceConfigKeys.validate(FinanceConfigKeys.REFUND_HOLIDAYS, "")).doesNotThrowAnyException();
        assertThatCode(() -> FinanceConfigKeys.validate(FinanceConfigKeys.REFUND_HOLIDAYS, "2026-12-25, 2027-01-01"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> FinanceConfigKeys.validate(FinanceConfigKeys.REFUND_HOLIDAYS, "25/12/2026"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void nullAndOverlongValues_areRejected_butUnrelatedKeysAreNotValidated() {
        assertThatThrownBy(() -> FinanceConfigKeys.validate("QUOTA_TUTOR", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> FinanceConfigKeys.validate("QUOTA_TUTOR", "x".repeat(256)))
                .isInstanceOf(InvalidRequestException.class);
        // Khóa không thuộc nhóm tài chính (QUOTA_*) giữ hành vi cũ: không kiểm tra nội dung.
        assertThatCode(() -> FinanceConfigKeys.validate("QUOTA_TUTOR", "bất kỳ")).doesNotThrowAnyException();
    }

    @Test
    void validate_reportAndInvoiceKeys() {
        FinanceConfigKeys.validate("REPORT_WEEKLY_ENABLED", "1");
        FinanceConfigKeys.validate("PAYOS_PAYOUT_FEE", "2950");
        FinanceConfigKeys.validate("REPORT_RECIPIENTS", "");
        FinanceConfigKeys.validate("REPORT_RECIPIENTS", "a@b.com, c@d.vn");
        FinanceConfigKeys.validate("INVOICE_EXCLUDED_EMAIL_SUFFIX", "@example.com");
        FinanceConfigKeys.validate("INVOICE_EXCLUDED_EMAIL_SUFFIX", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FinanceConfigKeys.validate("REPORT_WEEKLY_ENABLED", "2"))
                .isInstanceOf(com.lms.common.exception.InvalidRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FinanceConfigKeys.validate("REPORT_RECIPIENTS", "a@b.com, khong-phai-email"))
                .isInstanceOf(com.lms.common.exception.InvalidRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FinanceConfigKeys.validate("INVOICE_EXCLUDED_EMAIL_SUFFIX", "%@x.com"))
                .isInstanceOf(com.lms.common.exception.InvalidRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FinanceConfigKeys.validate("INVOICE_ISSUER_NAME", "  "))
                .isInstanceOf(com.lms.common.exception.InvalidRequestException.class);
    }
}
