package com.lms.invoice.dto;

import com.lms.invoice.entity.InvoiceStatus;
import com.lms.invoice.entity.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO chứng từ — không lộ entity; snapshot/dòng chi tiết chỉ nằm trong PDF, danh sách chỉ cần phần tóm tắt. */
public final class InvoiceDto {

    private InvoiceDto() {}

    /**
     * {@code refs}: mã nghiệp vụ mà chứng từ gắn vào, để các màn hình ghép chứng từ vào đúng dòng của mình — mã đơn (biên lai, phiếu hoàn),
     * mã yêu cầu rút (phiếu chi), mã thanh toán nợ (biên lai công nợ), kỳ YYYYMM (bảng kê tháng).
     */
    public record Res(Long id, String number, InvoiceType type, InvoiceStatus status, String currency, BigDecimal subtotal,
                      BigDecimal discount, BigDecimal total, LocalDateTime issuedAt, String relatedNumber, String description,
                      List<String> refs) {}

    public record AdminRes(Long id, String number, InvoiceType type, InvoiceStatus status, String currency, BigDecimal total,
                           LocalDateTime issuedAt, Long partyUserId, String partyName, String partyEmail, String relatedNumber,
                           List<String> refs) {}

    public record Line(int lineNo, String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {}

    /** Một mục của biểu đồ thành phần (vd thu nhập theo khóa học của bảng kê tháng). */
    public record Slice(String label, BigDecimal amount) {}

    /** Chi tiết một chứng từ: thông tin chung, các dòng, dữ liệu đã chụp (không có dữ liệu nhạy cảm — số điện thoại/tài khoản đã che) và thành phần (nếu có). */
    public record Detail(AdminRes header, BigDecimal subtotal, BigDecimal discount, List<Line> lines, java.util.Map<String, Object> details,
                         List<Slice> breakdown) {}

    public record AdminPage(List<AdminRes> items, long total, int page, int size) {}
}
