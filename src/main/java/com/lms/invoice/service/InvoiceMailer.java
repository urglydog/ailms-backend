package com.lms.invoice.service;

import com.lms.auth.service.EmailService;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gửi email chứng từ kèm PDF cho chủ chứng từ ngay sau khi phát hành (20/11/2026). Chỉ gửi với chứng từ MỚI (ngày chứng từ trong vòng
 * {@link #RECENT}) — chứng từ phát bù cho giao dịch cũ không gửi email, nếu không lần backfill đầu sẽ gửi hàng loạt thư về giao dịch từ nhiều tuần trước.
 * Best-effort: chạy ngoài transaction phát hành (chứng từ đã commit), lỗi chỉ ghi log và không bao giờ ném ra ngoài.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceMailer {

    static final Duration RECENT = Duration.ofHours(48);

    private final InvoiceQueryService queryService;
    private final EmailService emailService;

    public void sendIfRecent(Long invoiceId) {
        send(invoiceId, true);
    }

    /** Admin gửi lại email chứng từ cho người nhận (không giới hạn thời gian). Trả {@code false} nếu không gửi được. */
    public boolean resend(Long invoiceId) {
        return send(invoiceId, false);
    }

    private boolean send(Long invoiceId, boolean onlyRecent) {
        try {
            InvoiceQueryService.MailPayload m = queryService.mailPayload(invoiceId);
            if (m.toEmail() == null || m.toEmail().toLowerCase(java.util.Locale.ROOT).endsWith(".local")
                    || (onlyRecent && m.issuedAt().isBefore(LocalDateTime.now().minus(RECENT)))) {
                return false;
            }
            Copy c = copyOf(m.type());
            emailService.sendDocumentEmail(m.toEmail(), m.recipientName(), c.subject() + " " + m.number() + " - LinguaLearn", c.heading(), c.intro(),
                    c.numberLabel(), m.number(), c.amountLabel(), m.total(), m.issuedAt(), c.note(), c.linkPath(), c.linkLabel(), m.filename(), m.pdf());
            return true;
        } catch (RuntimeException e) {
            log.warn("Không gửi được email chứng từ #{}: {}", invoiceId, e.getMessage());
            return false;
        }
    }

    record Copy(String subject, String heading, String intro, String numberLabel, String amountLabel, String note, String linkPath, String linkLabel) {}

    /** Nội dung theo góc nhìn người nhận (CLAUDE.md mục 11): học viên "đã thanh toán/đã được hoàn", giảng viên "đã nhận". */
    static Copy copyOf(com.lms.invoice.entity.InvoiceType type) {
        return switch (type) {
            case RECEIPT -> new Copy("Biên lai thanh toán", "Thanh toán thành công",
                    "Cảm ơn bạn đã thanh toán. LinguaLearn đã ghi nhận đơn hàng của bạn, các khóa học đã sẵn sàng trong mục Khóa học của tôi. "
                            + "Biên lai điện tử được đính kèm trong email này.",
                    "Số biên lai", "Số tiền đã thanh toán",
                    "Đây là biên lai điện tử nội bộ, không phải hóa đơn giá trị gia tăng. Bạn có thể xem và tải lại biên lai tại mục",
                    "/payments", "Lịch sử giao dịch");
            case REFUND -> new Copy("Phiếu hoàn tiền", "Phiếu hoàn tiền của bạn",
                    "Khoản hoàn tiền của bạn đã được chuyển vào tài khoản ngân hàng bạn đã đăng ký. Phiếu hoàn tiền được đính kèm trong email này.",
                    "Số phiếu", "Số tiền đã hoàn",
                    "Nếu sau 24 giờ bạn chưa thấy tiền về tài khoản, hãy gửi yêu cầu hỗ trợ kèm số phiếu. Bạn có thể xem lại phiếu tại mục",
                    "/payments", "Lịch sử giao dịch");
            case PAYOUT -> new Copy("Phiếu chi rút tiền", "Phiếu chi rút tiền của bạn",
                    "Yêu cầu rút tiền của bạn đã hoàn tất, số tiền đã về tài khoản ngân hàng bạn đã liên kết. Phiếu chi được đính kèm trong email này. "
                            + "Phí chuyển tiền do LinguaLearn chi trả.",
                    "Số phiếu chi", "Số tiền đã nhận",
                    "Bạn có thể xem lại phiếu chi tại mục Thu nhập, phần",
                    "/instructor/earnings", "Lịch sử rút tiền");
            case DEBT_RECEIPT -> new Copy("Biên lai trả nợ", "Biên lai trả công nợ",
                    "LinguaLearn đã ghi nhận khoản thanh toán công nợ của bạn. Biên lai được đính kèm trong email này.",
                    "Số biên lai", "Số tiền đã trả",
                    "Bạn có thể xem lại biên lai và công nợ còn lại tại mục",
                    "/instructor/earnings", "Thu nhập");
            case STATEMENT -> new Copy("Bảng kê thu nhập tháng", "Bảng kê thu nhập tháng của bạn",
                    "LinguaLearn gửi bạn bảng kê thu nhập của tháng vừa qua, gồm thu nhập từng đơn, các khoản hoàn tiền, khoản đã rút và công nợ nếu có. "
                            + "Bảng kê được đính kèm trong email này.",
                    "Số bảng kê", "Biến động trong tháng",
                    "Bạn có thể xem lại các bảng kê tại mục Thu nhập, phần",
                    "/instructor/earnings", "Bảng kê thu nhập tháng");
        };
    }
}
