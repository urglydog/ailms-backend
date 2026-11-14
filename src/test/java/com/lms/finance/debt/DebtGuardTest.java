package com.lms.finance.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorDebtState;
import com.lms.finance.repository.InstructorDebtStateRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebtGuardTest {

    @Mock private InstructorDebtStateRepository stateRepository;
    @Mock private PaymentRefundRepository refundRepository;
    @Mock private SystemConfigService config;
    @InjectMocks private DebtGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(config.getInt("DEBT_RESTRICT_DAYS", 30)).thenReturn(30);
        lenient().when(config.getInt("DEBT_SUSPEND_DAYS", 90)).thenReturn(90);
    }

    private void inDebtFor(long days) {
        InstructorDebtState s = new InstructorDebtState();
        s.setInstructorId(7L);
        s.setDebtSince(LocalDateTime.now().minusDays(days));
        lenient().when(stateRepository.findById(7L)).thenReturn(Optional.of(s));
    }

    @Test
    void levelsFollowTheThirtyAndNinetyDayLadder() {
        assertThat(guard.levelOf(7L)).isZero(); // không có dòng nợ
        inDebtFor(5);
        assertThat(guard.levelOf(7L)).isEqualTo(1);
        inDebtFor(30);
        assertThat(guard.levelOf(7L)).isEqualTo(2);
        inDebtFor(89);
        assertThat(guard.levelOf(7L)).isEqualTo(2);
        inDebtFor(90);
        assertThat(guard.levelOf(7L)).isEqualTo(3);
        assertThat(guard.levelOf(null)).isZero();
    }

    @Test
    void restrictionsApplyFromLevelTwo_andSalesStopOnlyAtLevelThree() {
        inDebtFor(10);
        guard.assertCanPublish(7L);
        guard.assertCanPromote(7L);
        guard.assertCanSell(7L);

        inDebtFor(45);
        assertThatThrownBy(() -> guard.assertCanPublish(7L)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> guard.assertCanPromote(7L)).isInstanceOf(BusinessRuleViolationException.class);
        guard.assertCanSell(7L); // bậc 2 vẫn bán được

        inDebtFor(120);
        assertThatThrownBy(() -> guard.assertCanSell(7L)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anOpenAppealPausesTheRestrictions() {
        inDebtFor(120);
        when(refundRepository.existsOpenAppealByInstructor(7L)).thenReturn(true);

        assertThat(guard.levelOf(7L)).isEqualTo(1);
        guard.assertCanSell(7L);
        guard.assertCanPublish(7L);
    }

    @Test
    void suspendedInstructorIds_excludesThoseWithAnOpenAppeal() {
        when(stateRepository.findInstructorIdsInDebtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(7L, 8L));
        when(refundRepository.existsOpenAppealByInstructor(7L)).thenReturn(true);
        when(refundRepository.existsOpenAppealByInstructor(8L)).thenReturn(false);

        assertThat(guard.suspendedInstructorIds()).containsExactly(8L);
    }
}
