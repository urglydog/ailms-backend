package com.lms.finance.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.PayosFailureCategory;
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
import java.time.LocalDateTime;
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
    @Mock private com.lms.finance.payos.PayosOrderGuard payosOrderGuard;
    @Mock private PayosTransferLock transferLock;

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
        lenient().when(config.getInt(eq("PAYOS_MAX_ATTEMPTS"), anyInt())).thenReturn(3);
        lenient().when(config.getInt(eq("PAYOS_RETRY_COOLDOWN_MINUTES"), anyInt())).thenReturn(30);
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
    void manualRecordingGuard_isTheSharedServiceLayerGuard() {
        // Logic chặn nằm ở PayosOrderGuard (có test riêng); lớp này chỉ ủy quyền để mọi đường vào dùng chung một cửa.
        service.assertNoInFlightOrder(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L);

        verify(payosOrderGuard).assertNoUnsettled(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L);
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

    @Test
    void estimateFailing_isNotFatal_theBalanceAgainstTheAmountDecides() {
        approvedPayout(200_000);
        when(gateway.estimateCredit(any())).thenThrow(new IllegalArgumentException("No serializer found for class java.lang.Object"));
        when(gateway.balance()).thenReturn(1_000_000L);
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", null));

        assertThat(service.transferInstructorPayout(ADMIN, 5L).status()).isEqualTo(PayosOrderStatus.SUCCEEDED);
    }

    @Test
    void estimateFailing_butBalanceBelowTheAmount_stillStops() {
        approvedPayout(200_000);
        when(gateway.estimateCredit(any())).thenThrow(new IllegalArgumentException("boom"));
        when(gateway.balance()).thenReturn(50_000L);

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("không đủ");
        verify(gateway, never()).create(any());
    }

    // ── chính sách thử lại / phân loại lỗi / khóa (16/11/2026) ───────────────────────────────────

    private PayosPayoutOrder failedOrder(int attempt, PayosFailureCategory category, LocalDateTime completedAt) {
        PayosPayoutOrder o = new PayosPayoutOrder();
        o.setId(100L + attempt);
        o.setKind(PayosOrderKind.INSTRUCTOR_PAYOUT);
        o.setEntityId(5L);
        o.setAttempt(attempt);
        o.setReferenceId("LLP5-" + attempt);
        o.setAmount(200_000L);
        o.setBankBin("970407");
        o.setAccountLast4("3018");
        o.setStatus(PayosOrderStatus.FAILED);
        o.setFailureCategory(category);
        o.setCreatedAt(completedAt.minusMinutes(1));
        o.setCompletedAt(completedAt);
        return o;
    }

    private void history(PayosPayoutOrder... orders) {
        // findFirst… trả bản mới nhất, findBy… trả cả danh sách (mới → cũ), đúng như repository thật.
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(orders[0]));
        lenient().when(orderRepository.findByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(List.of(orders));
    }

    @Test
    void retryRightAfterAFailure_isBlockedByTheCooldown_evenWhenCalledDirectly() {
        approvedPayout(200_000);
        history(failedOrder(1, PayosFailureCategory.TRANSIENT, LocalDateTime.now().minusMinutes(2)));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("chờ");

        verify(gateway, never()).create(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void retryAfterTheCooldown_createsTheNextAttemptWithANewReference() {
        approvedPayout(200_000);
        fundsOk();
        history(failedOrder(1, PayosFailureCategory.TRANSIENT, LocalDateTime.now().minusMinutes(31)));
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", null));

        service.transferInstructorPayout(ADMIN, 5L);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(gateway).create(req.capture());
        assertThat(req.getValue().referenceId()).isEqualTo("LLP5-2");
    }

    @Test
    void afterTheMaximumNumberOfFailures_payosIsLocked_onlyManualRemains() {
        approvedPayout(200_000);
        LocalDateTime old = LocalDateTime.now().minusHours(5);
        history(failedOrder(3, PayosFailureCategory.TRANSIENT, old), failedOrder(2, PayosFailureCategory.TRANSIENT, old.minusHours(1)),
                failedOrder(1, PayosFailureCategory.TRANSIENT, old.minusHours(2)));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("3/3");

        verify(gateway, never()).create(any());
    }

    @Test
    void afterAnAccountFault_retryIsRefused_untilTheRecipientFixesTheAccount() {
        approvedPayout(200_000);
        history(failedOrder(1, PayosFailureCategory.ACCOUNT, LocalDateTime.now().minusHours(3)));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("tài khoản");

        verify(gateway, never()).create(any());
    }

    @Test
    void whenAnotherRequestHoldsTheLock_nothingIsSentToPayos() {
        approvedPayout(200_000);
        when(transferLock.acquire(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenThrow(new BusinessRuleViolationException("đang được một thao tác chi khác xử lý"));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("thao tác chi khác");

        verify(gateway, never()).create(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aDuplicateReferenceInsert_isReportedClearly_notAsAServerError() {
        approvedPayout(200_000);
        fundsOk();
        when(orderRepository.save(any(PayosPayoutOrder.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry LLP5-1"));

        assertThatThrownBy(() -> service.transferInstructorPayout(ADMIN, 5L))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("vừa được tạo");

        verify(gateway, never()).create(any());
    }

    private void payoutWithInstructor() {
        User ins = new User();
        ins.setId(9L);
        InstructorPayout p = new InstructorPayout();
        p.setStatus(PayoutStatus.APPROVED);
        p.setAmount(new BigDecimal(200_000));
        p.setBankCode("TCB");
        p.setHolderName("NGUYEN CHI THIEN");
        p.setAccountNumberEnc("v1:enc");
        p.setCode("PO-1");
        p.setInstructor(ins);
        when(payoutRepository.findById(5L)).thenReturn(Optional.of(p));
        when(accountService.decryptForAdmin("v1:enc")).thenReturn("19070154183018");
    }

    @Test
    void transientBankFailure_notifiesTheRecipientOnce_andKeepsThePayoutApproved() {
        payoutWithInstructor();
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("bank down", 400, "PAYOUT_BANK_SYSTEM_ERROR", "Hệ thống ngân hàng gián đoạn, vui lòng thử lại sau"));
        when(orderRepository.findByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L))
                .thenAnswer(inv -> stored == null ? List.of() : List.of(stored));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.status()).isEqualTo(PayosOrderStatus.FAILED);
        assertThat(res.failureCategory()).isEqualTo(PayosFailureCategory.TRANSIENT);
        verify(notificationService).notify(eq(9L), eq("PAYOS_TRANSFER_DELAYED"), anyString(), anyString(), anyString());
        verify(payoutService, never()).markFailed(anyString(), anyLong(), anyString());
    }

    @Test
    void accountFault_terminatesThePayout_andReleasesTheBalanceThroughTheNormalPath() {
        payoutWithInstructor();
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("bad", 400, "X", "Tài khoản nhận không tìm thấy"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.failureCategory()).isEqualTo(PayosFailureCategory.ACCOUNT);
        verify(payoutService).markFailed(eq(ADMIN), eq(5L), org.mockito.ArgumentMatchers.contains("tài khoản nhận không hợp lệ"));
        verify(notificationService, never()).notify(eq(9L), eq("PAYOS_TRANSFER_DELAYED"), anyString(), anyString(), anyString());
    }

    @Test
    void unclassifiedFailure_onlyTellsTheAdmins_doesNotGuessAnything() {
        payoutWithInstructor();
        fundsOk();
        when(gateway.create(any())).thenThrow(new APIException("weird", 400, "SOMETHING_NEW", "Lỗi lạ chưa từng thấy"));

        PayosDto.OrderRes res = service.transferInstructorPayout(ADMIN, 5L);

        assertThat(res.failureCategory()).isEqualTo(PayosFailureCategory.UNKNOWN);
        verify(payoutService, never()).markFailed(anyString(), anyLong(), anyString());
        verify(notificationService, never()).notify(eq(9L), anyString(), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(1L), eq("PAYOS_PAYOUT_FAILED"), anyString(), anyString(), anyString());
    }

    @Test
    void ordersList_exposesTheRetryStateOnlyOnTheLatestFailedOrder() {
        LocalDateTime recent = LocalDateTime.now().minusMinutes(3);
        PayosPayoutOrder latest = failedOrder(2, PayosFailureCategory.TRANSIENT, recent);
        PayosPayoutOrder older = failedOrder(1, PayosFailureCategory.TRANSIENT, recent.minusHours(1));
        when(orderRepository.findByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(List.of(latest, older));
        when(orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L)).thenReturn(Optional.of(latest));

        List<PayosDto.OrderRes> res = service.orders(PayosOrderKind.INSTRUCTOR_PAYOUT, 5L);

        assertThat(res.get(0).canRetry()).isFalse();
        assertThat(res.get(0).attemptsLeft()).isEqualTo(1);
        assertThat(res.get(0).nextRetryAt()).isNotNull();
        assertThat(res.get(1).canRetry()).isNull();
    }

    @Test
    void successFoundByTheBackgroundReconcile_tellsTheAdmins_butAnAdminTriggeredSuccessDoesNot() {
        approvedPayout(200_000);
        fundsOk();
        when(gateway.create(any())).thenReturn(result("SUCCEEDED", "NGUYEN CHI THIEN"));

        service.transferInstructorPayout(ADMIN, 5L);

        // Admin vừa bấm và thấy ngay kết quả: không cần thông báo thêm.
        verify(notificationService, never()).notify(anyLong(), eq("PAYOS_PAYOUT_SUCCEEDED"), anyString(), anyString(), anyString());

        PayosPayoutOrder open = new PayosPayoutOrder();
        open.setId(88L);
        open.setKind(PayosOrderKind.INSTRUCTOR_PAYOUT);
        open.setEntityId(5L);
        open.setReferenceId("LLP5-1");
        open.setAmount(200_000L);
        open.setStatus(PayosOrderStatus.SUBMITTED);
        open.setPayosPayoutId("po-9");
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(open));
        when(orderRepository.findById(88L)).thenReturn(Optional.of(open));
        when(gateway.get("po-9")).thenReturn(result("SUCCEEDED", null));

        service.reconcileOpenOrders();

        verify(notificationService).notify(eq(1L), eq("PAYOS_PAYOUT_SUCCEEDED"), anyString(), anyString(), anyString());
    }
}
