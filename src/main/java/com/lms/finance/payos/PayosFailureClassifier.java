package com.lms.finance.payos;

import com.lms.common.enums.PayosFailureCategory;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Phân loại nguyên nhân lệnh chi PayOS thất bại (16/11/2026). Tài liệu PayOS KHÔNG liệt kê bảng mã lỗi chi hộ chính thức, chỉ có phần mô tả tiếng
 * Việt trong ví dụ phản hồi; vì vậy chỉ nhận diện những cụm mô tả đã xác nhận trong tài liệu (và mã {@code PAYOUT_BANK_SYSTEM_ERROR} đã gặp thật),
 * còn lại là {@link PayosFailureCategory#UNKNOWN} — không bao giờ tự đoán, vì phân loại sai nhóm {@code ACCOUNT} sẽ tự hủy yêu cầu của người dùng.
 */
final class PayosFailureClassifier {

    /** Mô tả (đã bỏ dấu, chữ thường) cho thấy sự cố tạm thời phía hệ thống/ngân hàng. */
    private static final List<String> TRANSIENT_TEXT = List.of(
            "he thong ngan hang gian doan", "giao dich bi gian doan", "he thong gian doan");

    /** Mô tả cho thấy thông tin tài khoản nhận có vấn đề: thử lại với cùng thông tin sẽ không bao giờ thành công. */
    private static final List<String> ACCOUNT_TEXT = List.of(
            "tai khoan dich khong hop le", "tai khoan nhan khong tim thay", "ngan hang thu huong khong hop le");

    private PayosFailureClassifier() {}

    static PayosFailureCategory classify(String errorCode, String errorMessage) {
        if ("PAYOUT_BANK_SYSTEM_ERROR".equals(errorCode)) {
            return PayosFailureCategory.TRANSIENT;
        }
        String text = normalize(errorMessage);
        // Kiểm nhóm tài khoản trước: nếu một thông điệp vừa có "không hợp lệ" vừa có "gián đoạn" thì thận trọng coi là cần xem xét chứ không auto-hủy.
        boolean account = ACCOUNT_TEXT.stream().anyMatch(text::contains);
        boolean transientErr = TRANSIENT_TEXT.stream().anyMatch(text::contains);
        if (account && transientErr) {
            return PayosFailureCategory.UNKNOWN;
        }
        if (account) {
            return PayosFailureCategory.ACCOUNT;
        }
        if (transientErr) {
            return PayosFailureCategory.TRANSIENT;
        }
        return PayosFailureCategory.UNKNOWN;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
