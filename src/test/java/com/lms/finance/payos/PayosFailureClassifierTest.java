package com.lms.finance.payos;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.common.enums.PayosFailureCategory;
import org.junit.jupiter.api.Test;

class PayosFailureClassifierTest {

    @Test
    void knownBankSystemErrorCode_isTransient() {
        assertThat(PayosFailureClassifier.classify("PAYOUT_BANK_SYSTEM_ERROR", null)).isEqualTo(PayosFailureCategory.TRANSIENT);
    }

    @Test
    void documentedOutageDescriptions_areTransient_withOrWithoutAccents() {
        assertThat(PayosFailureClassifier.classify(null, "Hệ thống ngân hàng gián đoạn, vui lòng thử lại sau")).isEqualTo(PayosFailureCategory.TRANSIENT);
        assertThat(PayosFailureClassifier.classify("x", "HE THONG NGAN HANG GIAN DOAN")).isEqualTo(PayosFailureCategory.TRANSIENT);
        assertThat(PayosFailureClassifier.classify(null, "Giao dịch bị gián đoạn")).isEqualTo(PayosFailureCategory.TRANSIENT);
    }

    @Test
    void documentedRecipientAccountDescriptions_areAccountFaults() {
        assertThat(PayosFailureClassifier.classify(null, "Tài khoản đích không hợp lệ")).isEqualTo(PayosFailureCategory.ACCOUNT);
        assertThat(PayosFailureClassifier.classify(null, "Tài khoản nhận không tìm thấy")).isEqualTo(PayosFailureCategory.ACCOUNT);
        assertThat(PayosFailureClassifier.classify(null, "Ngân hàng thụ hưởng không hợp lệ")).isEqualTo(PayosFailureCategory.ACCOUNT);
    }

    @Test
    void ourOwnProblems_andAnythingUnrecognised_areUnknown_neverGuessedIntoAnAccountFault() {
        assertThat(PayosFailureClassifier.classify(null, "Số dư không đủ")).isEqualTo(PayosFailureCategory.UNKNOWN);
        assertThat(PayosFailureClassifier.classify(null, "Vượt hạn mức tối đa/ngày")).isEqualTo(PayosFailureCategory.UNKNOWN);
        assertThat(PayosFailureClassifier.classify("NEW_CODE", "Lỗi lạ")).isEqualTo(PayosFailureCategory.UNKNOWN);
        assertThat(PayosFailureClassifier.classify(null, null)).isEqualTo(PayosFailureCategory.UNKNOWN);
    }

    @Test
    void aMessageThatLooksBothWays_isUnknown_soNothingIsAutoTerminated() {
        assertThat(PayosFailureClassifier.classify(null, "Tài khoản đích không hợp lệ do hệ thống gián đoạn")).isEqualTo(PayosFailureCategory.UNKNOWN);
    }
}
