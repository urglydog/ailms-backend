package com.lms.finance.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.finance.entity.PayosPayoutOrder;
import com.lms.finance.repository.PayosPayoutOrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayosOrderGuardTest {

    @Mock private PayosPayoutOrderRepository repo;
    @InjectMocks private PayosOrderGuard guard;

    private void latest(PayosOrderStatus status) {
        PayosPayoutOrder o = new PayosPayoutOrder();
        o.setReferenceId("LLP5-1");
        o.setStatus(status);
        when(repo.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(o));
    }

    @Test
    void unsettledOrders_blockManualActions() {
        for (PayosOrderStatus st : new PayosOrderStatus[] {PayosOrderStatus.PENDING, PayosOrderStatus.SUBMITTED, PayosOrderStatus.UNKNOWN}) {
            latest(st);
            assertThat(guard.hasUnsettled(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).isTrue();
            assertThatThrownBy(() -> guard.assertNoUnsettled(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L))
                    .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("LLP5-1");
        }
    }

    @Test
    void settledOrFailedOrNoOrder_doNotBlock() {
        latest(PayosOrderStatus.FAILED);
        assertThatCode(() -> guard.assertNoUnsettled(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).doesNotThrowAnyException();
        latest(PayosOrderStatus.SUCCEEDED);
        assertThatCode(() -> guard.assertNoUnsettled(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).doesNotThrowAnyException();
        when(repo.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.DISPUTE_REFUND, 9L)).thenReturn(Optional.empty());
        assertThat(guard.hasUnsettled(PayosOrderKind.DISPUTE_REFUND, 9L)).isFalse();
    }
}
