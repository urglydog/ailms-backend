package com.lms.finance.payos;

/**
 * Cổng giao tiếp với PayOS "chi hộ" (14/11/2026). Tách thành interface để dịch vụ điều phối test được không cần gọi PayOS thật, và để
 * khi chưa bật/chưa cấu hình thì mọi lời gọi bị chặn từ trước khi chạm mạng.
 */
public interface PayosPayoutGateway {

    /** {@code referenceId} cũng là khóa idempotency: gửi lại cùng giá trị không tạo lệnh chi thứ hai. */
    record TransferRequest(String referenceId, long amount, String description, String bankBin, String accountNumber) {
        /** Không bao giờ in số tài khoản đầy đủ ra log. */
        @Override
        public String toString() {
            String n = accountNumber == null ? "" : accountNumber;
            return "TransferRequest[ref=" + referenceId + ", amount=" + amount + ", bin=" + bankBin
                    + ", account=****" + (n.length() >= 4 ? n.substring(n.length() - 4) : "") + "]";
        }
    }

    /** Kết quả (rút gọn) của 1 lệnh chi: {@code approvalState} của lệnh, {@code txState} của giao dịch con duy nhất. */
    record TransferResult(String payoutId, String approvalState, String txState, String resolvedAccountName,
                          String bankReference, String errorCode, String errorMessage) {}

    /** Công tắc trong {@code system_configs} (PAYOS_PAYOUT_ENABLED). */
    boolean isEnabled();

    /** Số dư tài khoản chi (VND). */
    long balance();

    /** Số tiền tài khoản chi cần có để chi lệnh này (đã gồm phí nếu có). */
    long estimateCredit(TransferRequest request);

    TransferResult create(TransferRequest request);

    TransferResult get(String payoutId);
}
