package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.common.enums.PaymentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentMineSearchTest {

    @Test
    void statusGroup_mapsUiTabsToPaymentStatuses() {
        assertThat(PaymentService.statusGroup("SUCCESS")).containsExactly(PaymentStatus.PAID);
        assertThat(PaymentService.statusGroup("pending")).containsExactly(PaymentStatus.PENDING);
        assertThat(PaymentService.statusGroup("FAILED")).containsExactlyInAnyOrder(PaymentStatus.FAILED, PaymentStatus.EXPIRED);
        assertThat(PaymentService.statusGroup("REFUNDED")).containsExactly(PaymentStatus.REFUNDED);
    }

    @Test
    void statusGroup_allOrUnknownOrNullMeansNoStatusFilter() {
        for (String g : new String[] {null, "ALL", "", "whatever"}) {
            List<PaymentStatus> r = PaymentService.statusGroup(g);
            assertThat(r).isNull();
        }
    }
}
