package com.lms.invoice.util;

import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Mã kiểm (checksum) của chứng từ: SHA-256 trên nội dung BẤT BIẾN (số, loại, ngày, tiền, dữ liệu đã chụp và từng dòng). Vì chứng từ không sửa sau khi phát hành nên cùng
 * chứng từ luôn cho cùng mã; in ở chân trang PDF để đối chiếu bản in/bản lưu với dữ liệu trên hệ thống.
 */
public final class InvoiceChecksum {

    private InvoiceChecksum() {}

    /** Chuỗi chuẩn hóa dùng để băm — thứ tự trường cố định, ngăn cách bằng ký tự không xuất hiện trong dữ liệu thường. */
    static String canonical(Invoice inv, List<InvoiceLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(inv.getNumber()).append('\u001f').append(inv.getType()).append('\u001f').append(inv.getIssuedAt()).append('\u001f')
                .append(inv.getCurrency()).append('\u001f').append(inv.getSubtotal().toPlainString()).append('\u001f')
                .append(inv.getDiscount().toPlainString()).append('\u001f').append(inv.getTotal().toPlainString()).append('\u001f')
                .append(inv.getSnapshotJson());
        for (InvoiceLine l : lines) {
            sb.append('\u001e').append(l.getLineNo()).append('\u001f').append(l.getDescription()).append('\u001f').append(l.getQuantity()).append('\u001f')
                    .append(l.getUnitPrice().toPlainString()).append('\u001f').append(l.getAmount().toPlainString());
        }
        return sb.toString();
    }

    /** SHA-256 đầy đủ (64 ký tự hex thường). */
    public static String sha256(Invoice inv, List<InvoiceLine> lines) {
        return sha256(canonical(inv, lines));
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }

    /** Dạng ngắn dễ đọc để in: 16 ký tự hex đầu, viết hoa, nhóm 4 (vd {@code 9F3A-12BC-77D0-E4A1}). */
    public static String display(String fullHex) {
        String h = fullHex.substring(0, 16).toUpperCase(java.util.Locale.ROOT);
        return h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16);
    }
}
