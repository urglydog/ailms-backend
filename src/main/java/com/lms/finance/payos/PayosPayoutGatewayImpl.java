package com.lms.finance.payos;

import com.lms.common.service.SystemConfigService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.model.v1.payouts.Payout;
import vn.payos.model.v1.payouts.PayoutRequests;
import vn.payos.model.v1.payouts.PayoutTransaction;

/**
 * Bản dùng SDK {@code payos-java}. Kênh chi hộ của PayOS CÓ THỂ dùng bộ khóa riêng: nếu đặt đủ 3 biến
 * {@code PAYOS_PAYOUT_CLIENT_ID/API_KEY/CHECKSUM_KEY} thì dùng bộ đó, không thì dùng lại khóa thanh toán sẵn có (cần kiểm tra trên trang
 * quản trị PayOS). Không khai báo thêm bean {@code PayOS} thứ hai (sẽ làm mơ hồ chỗ tiêm theo kiểu của luồng thanh toán).
 */
@Slf4j
@Component
public class PayosPayoutGatewayImpl implements PayosPayoutGateway {

    static final String KEY_ENABLED = "PAYOS_PAYOUT_ENABLED";

    private final PayOS client;
    private final SystemConfigService config;

    public PayosPayoutGatewayImpl(
            PayOS paymentClient,
            SystemConfigService config,
            @Value("${PAYOS_PAYOUT_CLIENT_ID:}") String clientId,
            @Value("${PAYOS_PAYOUT_API_KEY:}") String apiKey,
            @Value("${PAYOS_PAYOUT_CHECKSUM_KEY:}") String checksumKey) {
        this.config = config;
        boolean separate = !clientId.isBlank() && !apiKey.isBlank() && !checksumKey.isBlank();
        this.client = separate ? new PayOS(clientId, apiKey, checksumKey) : paymentClient;
    }

    @Override
    public boolean isEnabled() {
        return "1".equals(config.getString(KEY_ENABLED, "0").trim());
    }

    @Override
    public long balance() {
        String raw = client.payoutsAccount().balance().getBalance();
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    @Override
    public long estimateCredit(TransferRequest request) {
        Long value = client.payouts().estimateCredit(toRequest(request)).getEstimateCredit();
        return value == null ? request.amount() : value;
    }

    @Override
    public TransferResult create(TransferRequest request) {
        log.info("PayOS payout create: {}", request);
        return toResult(client.payouts().create(toRequest(request), request.referenceId()));
    }

    @Override
    public TransferResult get(String payoutId) {
        return toResult(client.payouts().get(payoutId));
    }

    private static PayoutRequests toRequest(TransferRequest r) {
        return PayoutRequests.builder()
                .referenceId(r.referenceId())
                .amount(r.amount())
                .description(r.description())
                .toBin(r.bankBin())
                .toAccountNumber(r.accountNumber())
                .category(List.of("other"))
                .build();
    }

    private static TransferResult toResult(Payout p) {
        PayoutTransaction tx = p.getTransactions() == null || p.getTransactions().isEmpty() ? null : p.getTransactions().get(0);
        return new TransferResult(
                p.getId(),
                p.getApprovalState() == null ? null : p.getApprovalState().name(),
                tx == null || tx.getState() == null ? null : tx.getState().name(),
                tx == null ? null : tx.getToAccountName(),
                tx == null ? null : tx.getReference(),
                tx == null ? null : tx.getErrorCode(),
                tx == null ? null : tx.getErrorMessage());
    }
}
