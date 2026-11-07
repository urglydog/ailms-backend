package com.lms.auth.service;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${lms.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${lms.mail.from-address:noreply@lms.local}")
    private String fromAddress;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Mã xác thực OTP - LinguaLearn");
            helper.setText(
                    "Mã xác thực của bạn là: " + otp + "\n"
                            + "Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.",
                    buildOtpEmailHtml(otp));

            mailSender.send(mimeMessage);
            log.info("Đã gửi OTP đến email: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email đến {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Không thể gửi email OTP, vui lòng thử lại sau.");
        }
    }

    /** Template HTML tối giản cho email OTP (27/09/2026) — trước đây dùng {@code SimpleMailMessage}
     * (text thuần) trông thiếu chuyên nghiệp. Theo đúng màu thương hiệu ở
     * {@code fe/tailwind.config.ts} (`accent` #1A6FE0, `ink` #0F172A, `ink-muted` #64748B), không
     * phụ thuộc template engine ngoài — chỉ 1 chuỗi HTML nội tuyến. */
    private String buildOtpEmailHtml(String otp) {
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;background:#ffffff;\">"
                + "<div style=\"text-align:center;margin-bottom:24px;\">"
                + "<span style=\"display:inline-block;width:36px;height:36px;background:#1A6FE0;border-radius:10px;color:#ffffff;font-weight:700;font-size:20px;line-height:36px;\">L</span>"
                + "<div style=\"margin-top:8px;font-size:18px;font-weight:700;color:#0F172A;\">LinguaLearn</div>"
                + "</div>"
                + "<h2 style=\"font-size:16px;color:#0F172A;text-align:center;margin:0 0 8px;\">Mã xác thực của bạn</h2>"
                + "<p style=\"font-size:14px;color:#64748B;text-align:center;margin:0 0 24px;\">Nhập mã bên dưới để hoàn tất xác thực tài khoản.</p>"
                + "<div style=\"background:#F1F5F9;border-radius:12px;padding:20px;text-align:center;margin-bottom:24px;\">"
                + "<span style=\"font-size:32px;font-weight:700;letter-spacing:8px;color:#1A6FE0;\">" + otp + "</span>"
                + "</div>"
                + "<p style=\"font-size:13px;color:#64748B;text-align:center;margin:0;\">Mã có hiệu lực trong <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>"
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:24px 0;\">"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0;\">Nếu bạn không yêu cầu mã này, hãy bỏ qua email này.</p>"
                + "</div>";
    }

    /** Email link đặt lại mật khẩu do Admin tạo (05/11/2026) — best-effort giống
     * {@link #sendDisputeResolutionEmail}: lỗi gửi mail KHÔNG throw, Admin vẫn thấy link ở UI để
     * gửi tay làm phương án dự phòng (xem {@code AuthService.generateAdminResetLink}). */
    public void sendPasswordResetLinkEmail(String toEmail, String resetLink, LocalDateTime expiresAt) {
        try {
            String expiresText = expiresAt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Đặt lại mật khẩu - LinguaLearn");
            helper.setText(
                    "Quản trị viên đã tạo link đặt lại mật khẩu cho tài khoản của bạn.\n\n"
                            + "Đặt lại mật khẩu: " + resetLink + "\n\n"
                            + "Link có hiệu lực đến " + expiresText + " và chỉ dùng được 1 lần. "
                            + "Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                    buildPasswordResetLinkHtml(resetLink, expiresText));

            mailSender.send(mimeMessage);
            log.info("Đã gửi link đặt lại mật khẩu đến email: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi link đặt lại mật khẩu đến {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildPasswordResetLinkHtml(String resetLink, String expiresText) {
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;background:#ffffff;\">"
                + "<div style=\"text-align:center;margin-bottom:24px;\">"
                + "<span style=\"display:inline-block;width:36px;height:36px;background:#1A6FE0;border-radius:10px;color:#ffffff;font-weight:700;font-size:20px;line-height:36px;\">L</span>"
                + "<div style=\"margin-top:8px;font-size:18px;font-weight:700;color:#0F172A;\">LinguaLearn</div>"
                + "</div>"
                + "<h2 style=\"font-size:16px;color:#0F172A;text-align:center;margin:0 0 8px;\">Đặt lại mật khẩu</h2>"
                + "<p style=\"font-size:14px;color:#64748B;text-align:center;margin:0 0 24px;\">Quản trị viên đã tạo link đặt lại mật khẩu cho tài khoản của bạn.</p>"
                + "<p style=\"text-align:center;margin:0 0 24px;\"><a href=\"" + resetLink + "\" style=\"display:inline-block;padding:12px 24px;background:#1A6FE0;color:#ffffff;border-radius:10px;font-size:14px;font-weight:700;text-decoration:none;\">Đặt lại mật khẩu</a></p>"
                + "<p style=\"font-size:13px;color:#64748B;text-align:center;margin:0;\">Link có hiệu lực đến <strong>" + expiresText + "</strong> và chỉ dùng được 1 lần.</p>"
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:24px 0;\">"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0;\">Nếu nút không bấm được, sao chép link: <br><span style=\"word-break:break-all;\">" + resetLink + "</span></p>"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:8px 0 0;\">Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>"
                + "</div>";
    }

    /** WishlistPriceDropService (26/09/2026, tính năng mới) — gọi từ job nền (consumer Redis),
     * không phải từ request HTTP trực tiếp. KHÔNG throw khi lỗi (khác {@link #sendOtpEmail}) —
     * đây là thông báo "best-effort", 1 email lỗi không được làm hỏng cả batch xử lý giảm giá
     * (xem vòng lặp try/catch từng học viên ở {@code WishlistPriceDropService.processJob}). */
    public void sendPriceDropEmail(String toEmail, String courseTitle, String courseSlug, BigDecimal oldPrice, BigDecimal newPrice) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("🔥 Khóa học trong danh sách yêu thích của bạn vừa giảm giá!");
            message.setText(String.format(
                    "Khóa học \"%s\" mà bạn đã lưu vào danh sách yêu thích vừa giảm giá từ %s xuống còn %s.%n%n"
                            + "Xem ngay: %s/courses/%s%n%n"
                            + "Nếu không muốn nhận email này nữa, bạn có thể bỏ khóa học khỏi danh sách yêu thích.",
                    courseTitle, formatVnd(oldPrice), formatVnd(newPrice), frontendBaseUrl, courseSlug));

            mailSender.send(message);
            log.info("Đã gửi email báo giảm giá đến {} cho khóa học \"{}\"", toEmail, courseTitle);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email báo giảm giá đến {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Email phản hồi khiếu nại giao dịch (23/10/2026) — trước đây kết quả xử lý khiếu nại CHỈ
     * báo qua notification trong app, user không nhận được gì qua email dù được chấp nhận hay từ
     * chối hoàn tiền. Best-effort giống {@link #sendPriceDropEmail} — lỗi gửi mail không được
     * làm hỏng việc resolve dispute trong DB (xem {@code DisputeService.resolve}).
     *
     * @param adminNote nội dung admin tự nhập ở ô "Ghi chú xử lý" — chính là đoạn admin nhắn
     *                  riêng cho khách, hệ thống tự bọc formal template xung quanh.
     */
    public void sendDisputeResolutionEmail(
            String toEmail, boolean refunded, String txnRef, String courseTitle, BigDecimal amount,
            String reason, String adminNote, String refundBankAccount) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(refunded
                    ? "Khiếu nại của bạn đã được chấp nhận — hoàn tiền"
                    : "Kết quả xử lý khiếu nại giao dịch " + txnRef);
            helper.setText(
                    (refunded ? "Khiếu nại của bạn đã được chấp nhận và sẽ được hoàn tiền.\n\n"
                            : "Khiếu nại của bạn không được chấp nhận hoàn tiền.\n\n")
                            + "Mã giao dịch: " + txnRef + "\nKhóa học: " + courseTitle + "\nSố tiền: " + formatVnd(amount)
                            + "\n\nGhi chú từ đội ngũ hỗ trợ: " + adminNote
                            + "\n\nXem chính sách hoàn tiền: " + frontendBaseUrl + "/legal/refund",
                    buildDisputeResolutionHtml(refunded, txnRef, courseTitle, amount, reason, adminNote, refundBankAccount));

            mailSender.send(mimeMessage);
            log.info("Da gui email ket qua khieu nai den {} cho giao dich {}", toEmail, txnRef);
        } catch (Exception e) {
            log.error("Loi khi gui email ket qua khieu nai den {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildDisputeResolutionHtml(
            boolean refunded, String txnRef, String courseTitle, BigDecimal amount,
            String reason, String adminNote, String refundBankAccount) {
        String statusColor = refunded ? "#16A34A" : "#64748B";
        String statusText = refunded ? "Đã chấp nhận hoàn tiền" : "Không chấp nhận hoàn tiền";
        String refundRow = refunded && refundBankAccount != null
                ? "<tr><td style=\"padding:6px 0;color:#64748B;\">Hoàn tiền tới</td><td style=\"padding:6px 0;color:#0F172A;text-align:right;font-weight:600;\">"
                        + refundBankAccount + "</td></tr>"
                : "";
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;background:#ffffff;\">"
                + "<div style=\"text-align:center;margin-bottom:24px;\">"
                + "<span style=\"display:inline-block;width:36px;height:36px;background:#1A6FE0;border-radius:10px;color:#ffffff;font-weight:700;font-size:20px;line-height:36px;\">L</span>"
                + "<div style=\"margin-top:8px;font-size:18px;font-weight:700;color:#0F172A;\">LinguaLearn</div>"
                + "</div>"
                + "<h2 style=\"font-size:16px;color:#0F172A;text-align:center;margin:0 0 4px;\">Kết quả xử lý khiếu nại</h2>"
                + "<p style=\"text-align:center;margin:0 0 20px;\"><span style=\"display:inline-block;padding:4px 12px;border-radius:999px;background:" + statusColor + "1A;color:" + statusColor + ";font-size:13px;font-weight:700;\">" + statusText + "</span></p>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px;margin-bottom:20px;\">"
                + "<tr><td style=\"padding:6px 0;color:#64748B;\">Mã giao dịch</td><td style=\"padding:6px 0;color:#0F172A;text-align:right;font-family:monospace;\">" + txnRef + "</td></tr>"
                + "<tr><td style=\"padding:6px 0;color:#64748B;\">Khóa học</td><td style=\"padding:6px 0;color:#0F172A;text-align:right;\">" + courseTitle + "</td></tr>"
                + "<tr><td style=\"padding:6px 0;color:#64748B;\">Số tiền</td><td style=\"padding:6px 0;color:#0F172A;text-align:right;font-weight:600;\">" + formatVnd(amount) + "</td></tr>"
                + refundRow
                + "</table>"
                + "<div style=\"background:#F1F5F9;border-radius:12px;padding:16px;margin-bottom:16px;\">"
                + "<p style=\"font-size:12px;color:#64748B;margin:0 0 4px;font-weight:600;\">Lý do bạn đã gửi</p>"
                + "<p style=\"font-size:14px;color:#0F172A;margin:0;\">" + reason + "</p>"
                + "</div>"
                + "<div style=\"background:#F1F5F9;border-radius:12px;padding:16px;margin-bottom:24px;\">"
                + "<p style=\"font-size:12px;color:#64748B;margin:0 0 4px;font-weight:600;\">Phản hồi từ đội ngũ hỗ trợ</p>"
                + "<p style=\"font-size:14px;color:#0F172A;margin:0;\">" + adminNote + "</p>"
                + "</div>"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0 0 8px;\">Xem đầy đủ <a href=\"" + frontendBaseUrl + "/legal/refund\" style=\"color:#1A6FE0;\">chính sách hoàn tiền</a> của LinguaLearn.</p>"
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:16px 0;\">"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0;\">Email tự động, vui lòng không trả lời trực tiếp email này.</p>"
                + "</div>";
    }

    /** Email xác nhận đã nhận ticket hỗ trợ (05/11/2026) — best-effort, lỗi gửi mail KHÔNG làm hỏng
     * việc tạo ticket. Mọi nội dung người dùng nhập đều được escape HTML vì ticket gửi được khi chưa đăng nhập. */
    public void sendSupportTicketReceivedEmail(
            String toEmail, String recipientName, String code, String categoryText, String subject, String message, LocalDateTime createdAt) {
        try {
            String sentAt = createdAt.format(SUPPORT_TIME_FORMAT);
            String text = "Kính gửi " + recipientName + ",\n\n"
                    + "Cảm ơn bạn đã liên hệ LinguaLearn. Chúng tôi đã nhận được yêu cầu hỗ trợ của bạn.\n\n"
                    + "Mã yêu cầu: " + code + "\nNhóm vấn đề: " + categoryText + "\nTiêu đề: " + subject + "\nThời gian gửi: " + sentAt + "\n\n"
                    + "Nội dung bạn đã gửi:\n" + message + "\n\n"
                    + "Các bước tiếp theo:\n"
                    + "1. Đội ngũ hỗ trợ tiếp nhận và xem xét yêu cầu của bạn.\n"
                    + "2. Chúng tôi phản hồi qua chính địa chỉ email này trong vòng 24-48 giờ.\n"
                    + "3. Với yêu cầu phức tạp, thời gian xử lý có thể lâu hơn; chúng tôi sẽ chủ động liên hệ nếu cần thêm thông tin.\n\n"
                    + "Vui lòng giữ lại mã " + code + " để tra cứu. Nếu cần bổ sung thông tin, hãy gửi yêu cầu mới tại "
                    + frontendBaseUrl + "/help kèm mã này.\n\n"
                    + "Trân trọng,\nĐội ngũ hỗ trợ LinguaLearn";

            String html = supportMailShell("Đã nhận yêu cầu hỗ trợ",
                    greeting(recipientName)
                            + paragraph("Cảm ơn bạn đã liên hệ LinguaLearn. Chúng tôi đã nhận được yêu cầu hỗ trợ của bạn và xin xác nhận thông tin như sau.")
                            + summaryTable(new String[][]{
                                    {"Mã yêu cầu", "<span style=\"font-family:monospace;font-weight:700;color:#1A6FE0;\">" + HtmlUtils.htmlEscape(code) + "</span>"},
                                    {"Nhóm vấn đề", HtmlUtils.htmlEscape(categoryText)},
                                    {"Tiêu đề", HtmlUtils.htmlEscape(subject)},
                                    {"Thời gian gửi", sentAt}})
                            + section("Nội dung bạn đã gửi", quotedBox(message))
                            + section("Các bước tiếp theo", orderedList(java.util.List.of(
                                    "Đội ngũ hỗ trợ tiếp nhận và xem xét yêu cầu của bạn.",
                                    "Chúng tôi phản hồi qua chính địa chỉ email này trong vòng <strong>24-48 giờ</strong>.",
                                    "Với yêu cầu phức tạp, thời gian xử lý có thể lâu hơn; chúng tôi sẽ chủ động liên hệ nếu cần thêm thông tin.")))
                            + section("Lưu ý", paragraph("Vui lòng giữ lại mã <strong>" + HtmlUtils.htmlEscape(code)
                                    + "</strong> để tra cứu. Nếu cần bổ sung thông tin, hãy gửi yêu cầu mới tại "
                                    + "<a href=\"" + frontendBaseUrl + "/help\" style=\"color:#1A6FE0;\">Trung tâm trợ giúp</a> kèm mã này."))
                            + closing("Trân trọng,", null, "Đội ngũ hỗ trợ LinguaLearn"));

            sendHtmlMail(toEmail, "Đã nhận yêu cầu hỗ trợ " + code + " - LinguaLearn", text, html);
            log.info("Đã gửi email xác nhận ticket {} đến {}", code, toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email xác nhận ticket {} đến {}: {}", code, toEmail, e.getMessage());
        }
    }

    /** 1 mục trong email phản hồi: {@code title} có thể rỗng (không in tiêu đề); {@code list} = true thì in {@code items} dạng đánh số. */
    public record SupportReplySection(String title, boolean list, String text, java.util.List<String> items) {}

    /** Nội dung email phản hồi ĐÃ thay placeholder — {@code EmailService} chỉ escape + dựng HTML, không biết gì về ticket. */
    public record SupportReplyContent(
            String greeting, String intro, java.util.List<SupportReplySection> sections,
            String closing, String signatureName, String signatureTeam) {}

    /** Email phản hồi của Admin cho ticket hỗ trợ (05/11/2026) — best-effort như {@link #sendSupportTicketReceivedEmail}.
     * Admin tùy chỉnh toàn bộ lời chào, mở đầu, tiêu đề + nội dung từng mục, lời kết, chữ ký; phần cố định chỉ còn
     * logo, bảng tóm tắt ticket, khung nội dung ban đầu (tùy chọn, truyền {@code null} để bỏ) và dòng "email tự động". */
    public void sendSupportTicketReplyEmail(
            String toEmail, String code, String categoryText, String subject, String statusText,
            String originalMessage, SupportReplyContent content) {
        try {
            StringBuilder text = new StringBuilder();
            text.append(content.greeting()).append("\n\n").append(content.intro()).append("\n\n")
                    .append("Mã yêu cầu: ").append(code).append("\nNhóm vấn đề: ").append(categoryText)
                    .append("\nTiêu đề: ").append(subject).append("\nTrạng thái: ").append(statusText).append("\n");
            for (SupportReplySection sec : content.sections()) {
                text.append("\n");
                if (sec.title() != null && !sec.title().isBlank()) {
                    text.append(sec.title()).append(":\n");
                }
                if (sec.list()) {
                    for (int i = 0; i < sec.items().size(); i++) {
                        text.append(i + 1).append(". ").append(sec.items().get(i)).append("\n");
                    }
                } else {
                    text.append(sec.text()).append("\n");
                }
            }
            text.append("\n").append(content.closing()).append("\n").append(content.signatureName()).append("\n").append(content.signatureTeam());

            StringBuilder body = new StringBuilder();
            body.append(greetingHtml(content.greeting()))
                    .append(paragraph(HtmlUtils.htmlEscape(content.intro())))
                    .append(summaryTable(new String[][]{
                            {"Mã yêu cầu", "<span style=\"font-family:monospace;font-weight:700;color:#1A6FE0;\">" + HtmlUtils.htmlEscape(code) + "</span>"},
                            {"Nhóm vấn đề", HtmlUtils.htmlEscape(categoryText)},
                            {"Tiêu đề", HtmlUtils.htmlEscape(subject)},
                            {"Trạng thái", "<strong>" + HtmlUtils.htmlEscape(statusText) + "</strong>"}}));
            for (SupportReplySection sec : content.sections()) {
                String inner = sec.list()
                        ? orderedList(sec.items().stream().map(HtmlUtils::htmlEscape).toList())
                        : paragraph(HtmlUtils.htmlEscape(sec.text()).replace("\n", "<br>"));
                body.append(sec.title() != null && !sec.title().isBlank() ? section(HtmlUtils.htmlEscape(sec.title()), inner) : inner);
            }
            if (originalMessage != null) {
                body.append(section("Nội dung yêu cầu ban đầu", quotedBox(originalMessage)));
            }
            body.append(closing(content.closing(), content.signatureName(), content.signatureTeam()));

            sendHtmlMail(toEmail, "Phản hồi yêu cầu hỗ trợ " + code + " - LinguaLearn", text.toString(),
                    supportMailShell("Phản hồi yêu cầu hỗ trợ", body.toString()));
            log.info("Đã gửi email phản hồi ticket {} đến {}", code, toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email phản hồi ticket {} đến {}: {}", code, toEmail, e.getMessage());
        }
    }

    private static final DateTimeFormatter SUPPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private void sendHtmlMail(String toEmail, String subject, String text, String html) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(text, html);
        mailSender.send(mimeMessage);
    }

    // Các helper dựng HTML email hỗ trợ — tham số kiểu "html" phải được escape/an toàn từ phía gọi, hàm chỉ chèn nguyên văn.

    private String supportMailShell(String heading, String bodyHtml) {
        return "<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;background:#ffffff;color:#0F172A;\">"
                + "<div style=\"text-align:center;margin-bottom:24px;\">"
                + "<span style=\"display:inline-block;width:36px;height:36px;background:#1A6FE0;border-radius:10px;color:#ffffff;font-weight:700;font-size:20px;line-height:36px;\">L</span>"
                + "<div style=\"margin-top:8px;font-size:18px;font-weight:700;\">LinguaLearn</div>"
                + "</div>"
                + "<h2 style=\"font-size:18px;text-align:center;margin:0 0 20px;\">" + heading + "</h2>"
                + bodyHtml
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:24px 0 12px;\">"
                + "<p style=\"font-size:12px;color:#94A3B8;text-align:center;margin:0;\">Email tự động từ hệ thống hỗ trợ LinguaLearn, vui lòng không trả lời trực tiếp email này.</p>"
                + "</div>";
    }

    private String greeting(String name) {
        return "<p style=\"font-size:14px;margin:0 0 12px;\">Kính gửi <strong>" + HtmlUtils.htmlEscape(name) + "</strong>,</p>";
    }

    /** Lời chào do Admin soạn nguyên dòng (đã thay placeholder) — khác {@link #greeting(String)} tự thêm "Kính gửi". */
    private String greetingHtml(String line) {
        return "<p style=\"font-size:14px;margin:0 0 12px;\"><strong>" + HtmlUtils.htmlEscape(line) + "</strong></p>";
    }

    private String paragraph(String html) {
        return "<p style=\"font-size:14px;line-height:1.7;color:#334155;margin:0 0 16px;\">" + html + "</p>";
    }

    private String section(String title, String bodyHtml) {
        return "<h3 style=\"font-size:14px;margin:20px 0 8px;color:#0F172A;\">" + title + "</h3>" + bodyHtml;
    }

    private String summaryTable(String[][] rows) {
        StringBuilder sb = new StringBuilder("<table style=\"width:100%;border-collapse:collapse;font-size:14px;margin:0 0 8px;\">");
        for (String[] row : rows) {
            sb.append("<tr><td style=\"padding:6px 0;color:#64748B;width:130px;vertical-align:top;\">").append(row[0])
                    .append("</td><td style=\"padding:6px 0;color:#0F172A;\">").append(row[1]).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private String quotedBox(String rawText) {
        return "<div style=\"background:#F1F5F9;border-radius:12px;padding:14px 16px;font-size:14px;line-height:1.7;color:#334155;margin:0 0 8px;\">"
                + HtmlUtils.htmlEscape(rawText).replace("\n", "<br>") + "</div>";
    }

    private String orderedList(java.util.List<String> itemsHtml) {
        StringBuilder sb = new StringBuilder("<ol style=\"font-size:14px;line-height:1.7;color:#334155;margin:0 0 16px;padding-left:20px;\">");
        itemsHtml.forEach(i -> sb.append("<li style=\"margin-bottom:4px;\">").append(i).append("</li>"));
        return sb.append("</ol>").toString();
    }

    private String closing(String line, String signatureName, String team) {
        return "<p style=\"font-size:14px;line-height:1.7;color:#334155;margin:24px 0 0;\">" + HtmlUtils.htmlEscape(line) + "<br>"
                + (signatureName != null && !signatureName.isBlank() ? "<strong>" + HtmlUtils.htmlEscape(signatureName) + "</strong><br>" : "")
                + HtmlUtils.htmlEscape(team) + "</p>";
    }


    private static String formatVnd(BigDecimal amount) {
        return String.format("%,.0fđ", amount);
    }
}
