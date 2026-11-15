package com.lms.finance.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import com.lms.common.enums.PayoutStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorPayout;
import com.lms.finance.entity.PayosPayoutOrder;
import com.lms.finance.payos.PayosPayoutGateway.TransferRequest;
import com.lms.finance.payos.PayosPayoutGateway.TransferResult;
import com.lms.finance.repository.InstructorPayoutRepository;
import com.lms.finance.repository.PayosPayoutOrderRepository;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.PayoutAccountService;
import com.lms.finance.service.PayoutService;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import com.lms.payment.service.DisputeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import vn.payos.exception.APIException;

@ExtendWith(MockitoExtension.class)
class PayosPayoutServiceTest {

    private static final String ADMIN = "admin@lms.local";

    @Mock private PayosPayoutGateway gateway;
    @Mock private PayosPayoutOrderRepository orderRepository;
    @Mock private InstructorPayoutRepository payoutRepository;
    @Mock private PayoutService payoutService;
    @Mock private PayoutAccountService accountService;
    @Mock private PaymentRefundRepository refundRepository;
    @Mock private PaymentDisputeRepository disputeRepository;
    @Mock private DisputeService disputeService;
    @Mock private SystemConfigService config;
    @Mock private FinanceAuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private PlatformTransactionManager txManager;

    @InjectMocks private PayosPayoutService service;

    private User admin;
    private PayosPayoutOrder stored; // bản ghi "trong DB" giả lập

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN);
        admin.setRole(Role.ADMIN);
        lenient().when(userRepository.findByEmail(ADMIN)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(gateway.isEnabled()).thenReturn(true);
        lenient().when(config.getDecimal(eq("PAYOS_PAYOUT_MAX_AMOUNT"), any())).thenReturn(new BigDecimal("5000000"));
        lenient().when(orderRepository.save(any(PayosPayoutOrder.class))).thenAnswer(inv -> {
            PayosPayoutOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(77L);
            }
            stored = o;
            return o;
        });
        lenient().when(orderRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored));
        lenient().when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(any(), anyLong())).thenReturn(Optional.empty());
    }

    private void approvedPayout(long amount) {
        InstructorPayout p = new InstructorPayout();
        p.setStatus(PayoutStatus.APPROVED);
        p.setAmount(new BigDecimal(amount));
        p.setBankCode("TCB");
        p.setHolderName("NGUYEN CHI THIEN");
        p.setAccountNumberEnc("v1:enc");
        when(payoutRepository.findById(5L)).thenReturn(Optional.of(p));
        when(accountService.decryptForAdmin("v1:enc")).thenReturn("19070154183018");
    }

    private void fundsOk() {
        when(gateway.estimateCredit(any())).thenReturn(200_000L);
        when(gateway.balance()).thenReturn(1_000_000L);
    }

    private TransferResult result(String tx, String name) {
        return new TransferResult("po-1", "COMPLETED", tx, name, "FT123", null, null);
    }

    @Test
    void disabled_blocksEverythingBeforeTouchingPayos() {
        when(gateway.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L)).isInstanceOf(BusinessRuleViolationException.class);

        verify(gateway, never()).create(any());
        verify(payoutRepository, never()).findById(anyLong());
    }

    @Test
    void payout_success_sendsTechcombankBinAndFullNumber_thenRecordsPaidThroughTheManualPath() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", "NGUYEN CHI THIEN"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(gateway).create(req.capture());
        assertThat(req.getValue().bankBin()).isEqualTo("970407");
        assertThat(req.getValue().accountNumber()).isEqualTo("19070154183018");
        assertThat(req.getValue().amount()).isEqualTo(200_000L);
        assertThat(req.getValue().referenceId()).isEqualTo("LLP5-1");
        assertThat(req.getValue().toString()).doesNotContain("19070154183018").contains("****3018");
        assertThat(res.status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
        assertThat(res.accountLast4()).isEqualTo("3018");
        assertThat(res.nameMismatch()).isFalse();
        verify(payoutService).markPaid(ADMIN, 5L, "PAYOS-FT123", null);
        // số tài khoản đầy đủ không được lưu vào bản ghi lệnh
        assertThat(stored.toString()).doesNotContain("19070154183018");
    }

    @Test
    void insufficientPayosBalance_stopsBeforeCreatingAnyOrder() {
        approvedPayout(200_000);
        when(gateway.estimateCredit(any())).thenReturn(200_000L);
        when(gateway.balance()).thenReturn(50_000L);

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("không đủ");

        verify(orderRepository, never()).save(any());
        verify(gateway, never()).create(any());
    }

    @Test
    void amountAboveTheCap_isRejected_soBigTransfersStayManual() {
        approvedPayout(6_000_000);

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L)).isInstanceOf(BusinessRuleViolationException.class);

        verify(gateway, never()).create(any());
    }

    @Test
    void payosRejectsTheRequest_orderFailed_adminNotified_moneyStateUntouched() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("bad account", 400, "INVALID_ACCOUNT", "Tài khoản không tồn tại"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.status()).isEqualTo(PayosOrderStatus.FAILED);
        assertThat(res.errorMessage()).contains("không tồn tại");
        verify(payoutService, never()).markPaid(anyString(), anyLong(), anyString(), any());
        verify(payoutService, never()).markFailed(anyString(), anyLong(), anyString());
        verify(notificationService).notify(eq(1L), eq("PAYOS_PAYOUT_FAILED"), anyString(), anyString(), anyString());
    }

    @Test
    void networkError_leavesOrderUnknown_andRetryReusesTheSameReference() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenThrow(new RuntimeException("timeout"));
        PayosDto.OrderRes first = service.transferInstructorPayout(ADMIN, 5L);
        assertThat(first.status()).isEqualTo(PayosOrderStatus.UNKNOWN);

        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(stored));
        org.mockito.Mockito.doReturn(result("SUCCEEDED", null)).when(gateway).create(any());
        PayosDto.OrderRes retry = service.transferInstructorPayout(ADMIN, 5L);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(gateway, org.mockito.Mockito.times(2)).create(req.capture());
        assertThat(req.getAllValues()).extracting(TransferRequest::referenceId).containsOnly("LLP5-1");
        assertThat(retry.status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
    }

    @Test
    void anInFlightOrder_cannotBeSubmittedTwice() {
        approvedPayout(200_000);
        PayosPayoutOrder inFlight = new PayosPayoutOrder();
        inFlight.setStatus(PayosOrderStatus.SUBMITTED);
        inFlight.setAttempt(1);
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L)).isInstanceOf(BusinessRuleViolationException.class);

        verify(gateway, never()).create(any());
    }

    @Test
    void successWithADifferentBankName_warnsAdmins() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", "TRAN VAN B"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.nameMismatch()).isTrue();
        verify(notificationService).notify(eq(1L), eq("PAYOS_NAME_MISMATCH"), anyString(), anyString(), anyString());
    }

    @Test
    void reconcile_turnsASubmittedOrderIntoPaid_whenPayosReportsSuccess() {
        PayosPayoutOrder o = new PayosPayoutOrder();
        o.setId(9L);
        o.setKind(PayosOrderKind.INSTRUCTOR_PAYOUT);
        o.setEntityId(5L);
        o.setReferenceId("LLP5-1");
        o.setAmount(200_000L);
        o.setAccountLast4("3018");
        o.setStatus(PayosOrderStatus.SUBMITTED);
        o.setPayosPayoutId("po-1");
        o.setCreatedByUserId(1L);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(o));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(o));
        when(gateway.get("po-1")).thenReturn(result("SUCCEEDED", null));

        service.reconcileOpenOrders();

        assertThat(o.getStatus()).isEqualTo(PayosOrderStatus.SUCCEEDED);
        verify(payoutService).markPaid(ADMIN, 5L, "PAYOS-FT123", null);
    }

    @Test
    void refundAccountParsing_acceptsClearInput_andRefusesAmbiguousOnes() {
        PayosPayoutService.ParsedAccount ok = PayosPayoutService.parseRefundAccount("MBBANK 0356307125");
        assertThat(ok.bankBin()).isEqualTo("970422");
        assertThat(ok.number()).isEqualTo("0356307125");
        assertThat(PayosPayoutService.parseRefundAccount("0356 3071 25 - Vietcombank").bankBin()).isEqualTo("970436");

        for (String bad : new String[] {null, "", "0356307125", "MBBANK", "Ngan hang abc 0356307125", "MBBANK 0356307125 hoac 0123456789", "MBBANK 12345"}) {
            assertThatThrownBy(() -> PayosPayoutService.parseRefundAccount(bad)).as(String.valueOf(bad))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Test
    void refund_withStructuredConfirmedAccount_sendsThatAccountAndTheHolderName() {
        Payment payment = new Payment();
        payment.setId(100L);
        PaymentDispute dispute = new PaymentDispute();
        dispute.setId(4L);
        dispute.setPayment(payment);
        dispute.setRefundBankCode("MB");
        dispute.setRefundAccountNumber("0356307125");
        dispute.setRefundAccountHolder("NGUYEN CHI THIEN");
        dispute.setRefundAccountConfirmed(true);
        PaymentRefund refund = new PaymentRefund();
        refund.setAmount(new BigDecimal("200000"));
        refund.setTransferStatus(RefundTransferStatus.PENDING_TRANSFER);
        when(disputeRepository.findById(4L)).thenReturn(Optional.of(dispute));
        when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.of(refund));
        fundsOk();
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", "NGUYEN CHI THIEN"));

        PayosDto.OrderRes res = service.transferRefund(ADMIN, 4L);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(gateway).create(req.capture());
        assertThat(req.getValue().bankBin()).isEqualTo("970422");
        assertThat(req.getValue().accountNumber()).isEqualTo("0356307125");
        assertThat(req.getValue().referenceId()).isEqualTo("LLR4-1");
        assertThat(res.holderNameEntered()).isEqualTo("NGUYEN CHI THIEN");
        assertThat(res.status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
        verify(disputeService).markTransferred(eq(ADMIN), eq(4L), any());
    }

    @Test
    void manualRecording_isBlockedWhileAPayosOrderIsInFlightOrUnknown_butAllowedAfterFailure() {
        for (PayosOrderStatus st : new PayosOrderStatus[] {PayosOrderStatus.PENDING, PayosOrderStatus.SUBMITTED, PayosOrderStatus.UNKNOWN}) {
            PayosPayoutOrder o = new PayosPayoutOrder();
            o.setReferenceId("LLP5-1");
            o.setStatus(st);
            when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(o));
            assertThatThrownBy(() -> service.assertNoInFlightOrder(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L))
                    .as(st.name()).isInstanceOf(BusinessRuleViolationException.class);
        }
        PayosPayoutOrder failed = new PayosPayoutOrder();
        failed.setReferenceId("LLP5-1");
        failed.setStatus(PayosOrderStatus.FAILED);
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(failed));
        service.assertNoInFlightOrder(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L);
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 6L)).thenReturn(Optional.empty());
        service.assertNoInFlightOrder(PayosOrderKind.INSTRUCTOR_PAYOUT, 6L);
    }

    @Test
    void retryOfAnUnknownOrder_firstAsksPayosWhetherItExists_andNeverCreatesItTwice() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenThrow(new RuntimeException("timeout"));
        assertThat(service.transferInstructorPayout(ADMIN, 5L).status()).isEqualTo(PayosOrderStatus.UNKNOWN);

        // Lần gửi đầu thực ra ĐÃ tới PayOS và thành công; gửi lại phải thấy nó qua tra theo mã tham chiếu.
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(stored));
        when(gateway.findByReferenceId("LLP5-1")).thenReturn(Optional.of(result("SUCCEEDED", "NGUYEN CHI THIEN")));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
        verify(gateway, org.mockito.Mockito.times(1)).create(any()); // chỉ lần đầu
        verify(payoutService).markPaid(ADMIN, 5L, "PAYOS-FT123", null);
    }

    @Test
    void idempotencyAlreadyExists_isNotAFailure_itIsResolvedByLookup() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("conflict", 400, "ERR", "Idempotency key đã tồn tại"));
        when(gateway.findByReferenceId("LLP5-1")).thenReturn(Optional.of(result("SUCCEEDED", null)));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
        verify(payoutService).markPaid(ADMIN, 5L, "PAYOS-FT123", null);
    }

    @Test
    void idempotencyAlreadyExists_whenTheLookupFindsNothing_staysUnknownInsteadOfFailed() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("conflict", 400, "ERR", "Idempotency key đã tồn tại"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.status()).isEqualTo(PayosOrderStatus.UNKNOWN);
        verify(payoutService, never()).markFailed(anyString(), anyLong(), anyString());
    }

    @Test
    void estimateSmallerThanTheAmount_isTreatedAsAFee_soTheBalanceMustCoverAmountPlusFee() {
        approvedPayout(200_000);
        when(gateway.estimateCredit(any())).thenReturn(5_000L); // chỉ là phí
        when(gateway.balance()).thenReturn(203_000L);           // đủ tiền chi nhưng thiếu phí

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("không đủ");
        verify(gateway, never()).create(any());
    }
}
