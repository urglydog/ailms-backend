package com.lms.support.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.Role;
import com.lms.common.enums.SupportTicketCategory;
import com.lms.common.enums.SupportTicketStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.support.dto.SupportDto;
import com.lms.support.entity.SupportTicket;
import com.lms.support.entity.SupportTicketAttachment;
import com.lms.support.repository.SupportTicketAttachmentRepository;
import com.lms.support.repository.SupportTicketRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ticket hỗ trợ (05/11/2026) — kênh liên hệ Admin cho cả khách chưa đăng nhập (vd quên mật khẩu,
 * không nhận được OTP). Mirror pattern {@code DisputeService}: minh chứng ảnh qua
 * {@link StorageService}, thông báo cho Admin, email xác nhận/phản hồi best-effort.
 * Endpoint tạo ticket là PUBLIC nên chống spam 2 tầng: theo IP (khách vãng lai) và theo email
 * nhận (tránh dùng form làm công cụ email-bombing 1 địa chỉ).
 */
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    /** MIME thật (Tika) -> đuôi file hợp lệ — chỉ ảnh, khác Dispute (cho cả video). */
    private static final Map<String, String> ALLOWED_MIME_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private static final String IP_KEY_PREFIX = "support:ticket:ip:";
    private static final String EMAIL_KEY_PREFIX = "support:ticket:email:";

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    @Value("${lms.rules.max-support-ticket-files}")
    private int maxFiles;

    @Value("${lms.rules.max-support-ticket-size-mb}")
    private long maxSizeMb;

    @Value("${lms.rules.support-ticket-ip-limit-per-hour}")
    private int ipLimitPerHour;

    @Value("${lms.rules.support-ticket-email-limit-per-day}")
    private int emailLimitPerDay;

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    /** Mã hiển thị SUP-{yyMMdd}-{NHÓM}-{id 5 số}: ngày tạo + nhóm vấn đề + số thứ tự — đọc mã là biết ticket thuộc loại gì, tạo khi nào. */
    static String buildCode(SupportTicket t, LocalDateTime createdAt) {
        return "SUP-" + createdAt.format(CODE_DATE) + "-" + t.getCategory().getCode() + "-" + String.format("%05d", t.getId());
    }

    @Transactional
    public SupportDto.CreateRes create(String clientIp, SupportDto.CreateReq req, List<MultipartFile> files) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        List<MultipartFile> evidence = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (evidence.size() > maxFiles) {
            throw new BusinessRuleViolationException("Chỉ được đính kèm tối đa " + maxFiles + " ảnh minh chứng");
        }
        // Kiểm tra file TRƯỚC khi tính quota — yêu cầu sai định dạng không nên tiêu hao lượt gửi của người dùng.
        evidence.forEach(this::validateEvidence);

        enforceLimit(IP_KEY_PREFIX + clientIp, ipLimitPerHour, Duration.ofHours(1),
                "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau 1 giờ");
        enforceLimit(EMAIL_KEY_PREFIX + email, emailLimitPerDay, Duration.ofDays(1),
                "Email này đã gửi quá nhiều yêu cầu hôm nay, vui lòng thử lại vào ngày mai");

        SupportTicket ticket = new SupportTicket();
        // `code` NOT NULL + UNIQUE nhưng mã thật cần id (sinh sau INSERT) — tạm mã ngẫu nhiên, đổi ngay trong cùng transaction.
        ticket.setCode("TMP-" + UUID.randomUUID().toString().replace("-", ""));
        ticket.setEmail(email);
        ticket.setCategory(req.category());
        ticket.setSubject(req.subject().trim());
        ticket.setMessage(req.message().trim());
        ticket.setStatus(SupportTicketStatus.OPEN);
        SupportTicket saved = ticketRepository.save(ticket);
        LocalDateTime createdAt = saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now();
        saved.setCode(buildCode(saved, createdAt));
        saved = ticketRepository.save(saved);

        for (MultipartFile file : evidence) {
            attachmentRepository.save(upload(saved, file));
        }

        String code = saved.getCode();
        SupportTicket created = saved;
        userRepository.findByRole(Role.ADMIN).forEach(admin ->
                notificationService.notify(
                        admin.getId(),
                        "SUPPORT_TICKET",
                        "Yêu cầu hỗ trợ mới " + code,
                        created.getSubject(),
                        "/admin/support"
                ));

        emailService.sendSupportTicketReceivedEmail(
                email, recipientName(email), code, categoryText(saved.getCategory()), saved.getSubject(), saved.getMessage(), createdAt);
        return new SupportDto.CreateRes(code);
    }

    private void enforceLimit(String key, int limit, Duration ttl, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private String detectExtension(MultipartFile file) {
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("Ảnh minh chứng vượt quá " + maxSizeMb + "MB");
        }
        String detectedMime;
        try (InputStream sniff = file.getInputStream()) {
            detectedMime = tika.detect(sniff);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file minh chứng: " + e.getMessage());
        }
        String extension = ALLOWED_MIME_TO_EXTENSION.get(detectedMime);
        if (extension == null) {
            throw new InvalidRequestException("Định dạng minh chứng không được hỗ trợ (chỉ nhận ảnh jpg/png/webp)");
        }
        return extension;
    }

    private void validateEvidence(MultipartFile file) {
        detectExtension(file);
    }

    private SupportTicketAttachment upload(SupportTicket ticket, MultipartFile file) {
        String extension = detectExtension(file);
        String mime = "image/" + ("jpg".equals(extension) ? "jpeg" : extension);
        String key = "support-evidence/" + ticket.getId() + "/" + UUID.randomUUID() + "." + extension;
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), mime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được minh chứng lên kho lưu trữ: " + e.getMessage());
        }
        SupportTicketAttachment attachment = new SupportTicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileUrl(url);
        attachment.setFileType(extension);
        attachment.setFileSize(file.getSize());
        return attachment;
    }

    @Transactional(readOnly = true)
    public List<SupportDto.AdminRes> listForAdmin(SupportTicketStatus status) {
        List<SupportTicket> tickets = status == null
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByStatusOrderByCreatedAtDesc(status);
        return tickets.stream().map(this::toAdminRes).toList();
    }

    @Transactional(readOnly = true)
    public SupportDto.OpenCountRes countOpen() {
        return new SupportDto.OpenCountRes(ticketRepository.countByStatus(SupportTicketStatus.OPEN));
    }

    @Transactional
    public SupportDto.AdminRes update(String adminEmail, Long id, SupportDto.UpdateReq req) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupportTicket", id));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        SupportDto.EmailContent cleaned = cleanContent(req.content());
        boolean hasReply = cleaned != null;
        if (req.status() == SupportTicketStatus.RESOLVED && !hasReply
                && ticket.getAdminContent() == null && ticket.getAdminReply() == null) {
            throw new BusinessRuleViolationException("Vui lòng soạn phản hồi cho người dùng trước khi đánh dấu đã xử lý");
        }

        ticket.setStatus(req.status());
        EmailService.SupportReplyContent resolved = null;
        if (hasReply) {
            resolved = resolveContent(cleaned, ticket, nameFor(ticket.getEmail(), userRepository.findByEmail(ticket.getEmail())),
                    admin.getFullName() != null && !admin.getFullName().isBlank() ? admin.getFullName() : "Quản trị viên");
            ticket.setAdminContent(writeJson(cleaned));
            ticket.setAdminReply(flatten(resolved));
        }
        if (req.status() == SupportTicketStatus.RESOLVED) {
            ticket.setResolvedBy(admin);
            ticket.setResolvedAt(LocalDateTime.now());
        } else {
            ticket.setResolvedBy(null);
            ticket.setResolvedAt(null);
        }
        SupportTicket saved = ticketRepository.save(ticket);

        // Chỉ gửi mail khi Admin thực sự soạn phản hồi — chỉ đổi trạng thái thì không làm phiền người dùng.
        if (resolved != null) {
            boolean includeOriginal = cleaned.includeOriginal() == null || cleaned.includeOriginal();
            emailService.sendSupportTicketReplyEmail(
                    saved.getEmail(), saved.getCode(), categoryText(saved.getCategory()), saved.getSubject(),
                    statusText(saved.getStatus()), includeOriginal ? saved.getMessage() : null, resolved);
        }
        return toAdminRes(saved);
    }

    /** Trả null nếu Admin không soạn gì (không có mục nào có nội dung). Bỏ mục rỗng, ép mỗi dòng danh sách về 1 dòng
     * (xuống dòng trong 1 dòng sẽ phá format danh sách đánh số trong email). */
    private static SupportDto.EmailContent cleanContent(SupportDto.EmailContent in) {
        if (in == null || in.sections() == null) {
            return null;
        }
        List<SupportDto.Section> sections = new ArrayList<>();
        for (SupportDto.Section sec : in.sections()) {
            String title = sec.title() == null ? "" : sec.title().trim();
            if (sec.type() == SupportDto.SectionType.LIST) {
                List<String> items = sec.items() == null ? List.of() : sec.items().stream()
                        .filter(Objects::nonNull)
                        .map(x -> x.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim())
                        .filter(x -> !x.isEmpty())
                        .toList();
                if (!items.isEmpty()) {
                    sections.add(new SupportDto.Section(title, SupportDto.SectionType.LIST, null, items));
                }
            } else {
                String text = sec.text() == null ? "" : sec.text().trim();
                if (!text.isEmpty()) {
                    sections.add(new SupportDto.Section(title, SupportDto.SectionType.TEXT, text, null));
                }
            }
        }
        if (sections.isEmpty()) {
            return null;
        }
        return new SupportDto.EmailContent(trimToNull(in.greeting()), trimToNull(in.intro()), sections, trimToNull(in.closing()),
                trimToNull(in.signatureName()), trimToNull(in.signatureTeam()), in.includeOriginal());
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Thay placeholder {name}/{code}/{subject}/{category} trong mọi chuỗi và điền mặc định cho phần Admin bỏ trống. */
    private EmailService.SupportReplyContent resolveContent(
            SupportDto.EmailContent c, SupportTicket ticket, String recipientName, String adminName) {
        Map<String, String> vars = Map.of(
                "{name}", recipientName, "{code}", ticket.getCode(),
                "{subject}", ticket.getSubject(), "{category}", categoryText(ticket.getCategory()));
        List<EmailService.SupportReplySection> sections = c.sections().stream()
                .map(sec -> new EmailService.SupportReplySection(
                        fill(sec.title(), vars),
                        sec.type() == SupportDto.SectionType.LIST,
                        fill(sec.text(), vars),
                        sec.items() == null ? List.of() : sec.items().stream().map(i -> fill(i, vars)).toList()))
                .toList();
        return new EmailService.SupportReplyContent(
                fill(c.greeting() != null ? c.greeting() : "Kính gửi {name},", vars),
                fill(c.intro() != null ? c.intro() : "Về yêu cầu hỗ trợ {code}, chúng tôi xin phản hồi như sau.", vars),
                sections,
                fill(c.closing() != null ? c.closing() : "Trân trọng,", vars),
                fill(c.signatureName() != null ? c.signatureName() : adminName, vars),
                fill(c.signatureTeam() != null ? c.signatureTeam() : "Đội ngũ hỗ trợ LinguaLearn", vars));
    }

    private static String fill(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Bản text phẳng của phản hồi — lưu ở {@code admin_reply} để hiển thị nhanh và cho điều kiện "đã có phản hồi". */
    private static String flatten(EmailService.SupportReplyContent c) {
        StringBuilder sb = new StringBuilder();
        for (EmailService.SupportReplySection sec : c.sections()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            if (sec.title() != null && !sec.title().isBlank()) {
                sb.append(sec.title()).append(":\n");
            }
            if (sec.list()) {
                for (int i = 0; i < sec.items().size(); i++) {
                    sb.append(i + 1).append(". ").append(sec.items().get(i)).append(i < sec.items().size() - 1 ? "\n" : "");
                }
            } else {
                sb.append(sec.text());
            }
        }
        return sb.toString();
    }

    private String writeJson(SupportDto.EmailContent c) {
        try {
            return objectMapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Không lưu được nội dung phản hồi: " + e.getMessage());
        }
    }

    private SupportDto.EmailContent readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SupportDto.EmailContent.class);
        } catch (JsonProcessingException e) {
            return null; // dữ liệu hỏng không được làm sập cả danh sách ticket của Admin
        }
    }

    /** Tên trong lời chào email: họ tên nếu email khớp tài khoản, không thì phần trước @ — luôn có giá trị. */
    private String recipientName(String email) {
        return nameFor(email, userRepository.findByEmail(email));
    }

    private static String nameFor(String email, Optional<User> account) {
        return account
                .map(User::getFullName)
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
    }

    private static String categoryText(SupportTicketCategory category) {
        return switch (category) {
            case ACCOUNT -> "Tài khoản & đăng nhập";
            case PAYMENT -> "Thanh toán & hoàn tiền";
            case COURSE -> "Khóa học & nội dung";
            case LEARNING -> "Học tập: quiz, flashcard, học liệu";
            case AI_FEATURES -> "Tính năng AI";
            case EXAM -> "Thi & giám sát";
            case CERTIFICATE -> "Chứng chỉ";
            case COMMUNITY -> "Cộng đồng & đánh giá";
            case LIVE -> "Buổi học trực tiếp";
            case INSTRUCTOR -> "Dành cho giảng viên";
            case PRIVACY -> "Quyền riêng tư & dữ liệu";
            case TECHNICAL -> "Lỗi kỹ thuật";
            case OTHER -> "Vấn đề khác";
        };
    }

    private static String statusText(SupportTicketStatus status) {
        return switch (status) {
            case OPEN -> "Đã tiếp nhận";
            case IN_PROGRESS -> "Đang xử lý";
            case RESOLVED -> "Đã xử lý";
        };
    }

    private SupportDto.AdminRes toAdminRes(SupportTicket t) {
        Optional<User> account = userRepository.findByEmail(t.getEmail());
        Long userId = account.map(User::getId).orElse(null);
        List<SupportDto.AttachmentRes> attachments = attachmentRepository.findByTicket_IdOrderByIdAsc(t.getId()).stream()
                .map(a -> new SupportDto.AttachmentRes(a.getId(), a.getFileUrl(), a.getFileType()))
                .toList();
        return new SupportDto.AdminRes(
                t.getId(), t.getCode(), t.getEmail(), userId, nameFor(t.getEmail(), account), t.getCategory(), t.getSubject(), t.getMessage(),
                t.getStatus(), t.getAdminReply(), readJson(t.getAdminContent()),
                t.getResolvedBy() != null ? t.getResolvedBy().getEmail() : null,
                t.getResolvedAt(), t.getCreatedAt(), attachments);
    }
}
