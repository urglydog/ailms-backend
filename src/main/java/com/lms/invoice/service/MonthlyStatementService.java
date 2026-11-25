package com.lms.invoice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.service.SystemConfigService;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.service.InvoiceIssueService.StatementLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Bảng kê thu nhập tháng cho giảng viên (STM, 20/11/2026): mỗi giảng viên có hoạt động sổ cái/rút tiền trong tháng vừa xong nhận một bảng kê gồm từng khoản
 * thu nhập (kèm tỷ lệ phần nền tảng), hoàn tiền bị trừ, điều chỉnh, trả nợ và các lần rút đã chi. Tài khoản demo bị loại. Gọi lại là idempotent
 * (một bảng kê/giảng viên/tháng). Email cho giảng viên chỉ gửi khi bật {@code STATEMENT_EMAIL_INSTRUCTORS}; tắt thì giảng viên vẫn tải được trên giao diện.
 */
@Slf4j
@Service
public class MonthlyStatementService {

    static final int MAX_LINES = 300;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String MATCH_NOTHING = "%\u0001";

    @PersistenceContext
    private EntityManager em;

    private final InvoiceIssueService issueService;
    private final InvoiceMailer mailer;
    private final UserRepository userRepository;
    private final SystemConfigService config;

    public MonthlyStatementService(InvoiceIssueService issueService, InvoiceMailer mailer, UserRepository userRepository, SystemConfigService config) {
        this.issueService = issueService;
        this.mailer = mailer;
        this.userRepository = userRepository;
        this.config = config;
    }

    /** Mỗi giờ: phát bảng kê cho tháng vừa xong (sau 6 giờ sáng) với các giảng viên chưa có. Idempotent nên bù được sau khi server tắt. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 420_000)
    @SchedulerLock(name = "MonthlyStatementService.generate", lockAtMostFor = "PT1H", lockAtLeastFor = "PT55M")
    public void run() {
        try {
            LocalDateTime now = LocalDateTime.now();
            if (now.getHour() >= 6) {
                YearMonth last = YearMonth.from(now.toLocalDate()).minusMonths(1);
                // Bù các tháng đã qua kể từ tháng đầu tiên có phát sinh (tối đa 24 tháng gần nhất); chỉ tháng vừa xong mới gửi email cho giảng viên.
                YearMonth first = firstActivityMonth(last);
                int n = 0;
                for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
                    n += issueForMonth(m, m.equals(last));
                }
                if (n > 0) {
                    log.info("Đã phát hành {} bảng kê thu nhập tháng", n);
                }
            }
        } catch (RuntimeException e) {
            log.error("Lỗi phát hành bảng kê thu nhập tháng: {}", e.getMessage());
        }
    }

    /** Tháng đầu tiên có phát sinh sổ cái (không sớm hơn 24 tháng trước {@code last}); chưa có phát sinh nào thì trả {@code last}. */
    private YearMonth firstActivityMonth(YearMonth last) {
        Object min = em.createNativeQuery("SELECT MIN(created_at) FROM instructor_ledger_entries").getSingleResult();
        YearMonth earliest = last.minusMonths(23);
        if (min instanceof Timestamp t) {
            YearMonth first = YearMonth.from(t.toLocalDateTime());
            return first.isBefore(earliest) ? earliest : first;
        }
        return last;
    }

    public int issueForMonth(YearMonth ym) {
        return issueForMonth(ym, true);
    }

    /** {@code allowEmail}: có gửi email cho giảng viên (khi bật cấu hình) — tắt với các tháng bù để không gửi hàng loạt thư cũ. */
    public int issueForMonth(YearMonth ym, boolean allowEmail) {
        Timestamp from = Timestamp.valueOf(ym.atDay(1).atStartOfDay());
        Timestamp to = Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());
        String suffix = config.getString("INVOICE_EXCLUDED_EMAIL_SUFFIX", "@example.com");
        String excluded = suffix == null || suffix.isBlank() ? MATCH_NOTHING : "%" + suffix.trim().toLowerCase(Locale.ROOT);
        boolean email = allowEmail && config.getInt("STATEMENT_EMAIL_INSTRUCTORS", 0) == 1;
        Query ids = em.createNativeQuery("SELECT DISTINCT x.instructor_id FROM ("
                + "SELECT e.instructor_id FROM instructor_ledger_entries e WHERE e.created_at >= :from AND e.created_at < :to "
                + "AND e.entry_type IN ('EARNING','REFUND_REVERSAL','ADJUSTMENT','DEBT_REPAYMENT') "
                + "UNION SELECT o.instructor_id FROM instructor_payouts o WHERE o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to) x "
                + "JOIN users u ON u.id = x.instructor_id WHERE LOWER(u.email) NOT LIKE :ex")
                .setParameter("from", from).setParameter("to", to).setParameter("ex", excluded);
        int issued = 0;
        for (Object id : ids.getResultList()) {
            try {
                Optional<Invoice> inv = statementFor(((Number) id).longValue(), ym, from, to);
                if (inv.isPresent()) {
                    issued++;
                    if (email) {
                        mailer.sendIfRecent(inv.get().getId());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Không phát hành được bảng kê tháng {} cho giảng viên {}: {}", ym, id, e.getMessage());
            }
        }
        return issued;
    }

    @SuppressWarnings("unchecked")
    private Optional<Invoice> statementFor(long instructorId, YearMonth ym, Timestamp from, Timestamp to) {
        User instructor = userRepository.findById(instructorId).orElse(null);
        if (instructor == null) {
            return Optional.empty();
        }
        List<Object[]> entries = em.createNativeQuery("SELECT e.entry_type, e.amount, e.created_at, e.note, p.txn_ref, p.amount, p.platform_rate, c.title "
                + "FROM instructor_ledger_entries e LEFT JOIN payments p ON p.id = e.payment_id LEFT JOIN courses c ON c.id = p.course_id "
                + "WHERE e.instructor_id = :id AND e.created_at >= :from AND e.created_at < :to "
                + "AND e.entry_type IN ('EARNING','REFUND_REVERSAL','ADJUSTMENT','DEBT_REPAYMENT') ORDER BY e.id")
                .setParameter("id", instructorId).setParameter("from", from).setParameter("to", to).getResultList();
        List<Object[]> payouts = em.createNativeQuery("SELECT o.code, o.amount, o.paid_at FROM instructor_payouts o WHERE o.instructor_id = :id "
                + "AND o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to ORDER BY o.id")
                .setParameter("id", instructorId).setParameter("from", from).setParameter("to", to).getResultList();

        BigDecimal earnings = BigDecimal.ZERO;
        BigDecimal reversals = BigDecimal.ZERO;
        BigDecimal adjustments = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        BigDecimal paidOut = BigDecimal.ZERO;
        BigDecimal platformShare = BigDecimal.ZERO;
        long orders = 0;
        List<StatementLine> lines = new ArrayList<>();
        for (Object[] r : entries) {
            String type = String.valueOf(r[0]);
            BigDecimal amount = (BigDecimal) r[1];
            String course = r[7] == null ? "" : String.valueOf(r[7]);
            String order = r[4] == null ? "" : " (order " + r[4] + ")";
            switch (type) {
                case "EARNING" -> {
                    earnings = earnings.add(amount);
                    orders++;
                    BigDecimal paid = r[5] == null ? null : (BigDecimal) r[5];
                    String split = "";
                    if (paid != null) {
                        platformShare = platformShare.add(paid.subtract(amount));
                        BigDecimal rate = r[6] == null ? null : ((BigDecimal) r[6]).movePointRight(2).stripTrailingZeros();
                        split = ": learner paid " + paid.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + " VND"
                                + (rate == null ? "" : ", platform share " + rate.toPlainString() + "%");
                    }
                    lines.add(new StatementLine("Earning - " + course + order + split, amount));
                }
                case "REFUND_REVERSAL" -> {
                    reversals = reversals.add(amount);
                    lines.add(new StatementLine("Refund reversal - " + course + order, amount));
                }
                case "ADJUSTMENT" -> {
                    adjustments = adjustments.add(amount);
                    lines.add(new StatementLine("Adjustment" + (r[3] == null ? "" : ": " + r[3]), amount));
                }
                default -> {
                    debt = debt.add(amount);
                    lines.add(new StatementLine("Debt repayment", amount));
                }
            }
        }
        for (Object[] r : payouts) {
            BigDecimal amount = (BigDecimal) r[1];
            paidOut = paidOut.add(amount);
            LocalDateTime paidAt = ((Timestamp) r[2]).toLocalDateTime();
            lines.add(new StatementLine("Payout " + r[0] + " paid on " + paidAt.format(DAY), amount.negate()));
        }
        BigDecimal net = earnings.add(reversals).add(adjustments).add(debt).subtract(paidOut);
        if (lines.size() > MAX_LINES) {
            List<StatementLine> rest = lines.subList(MAX_LINES - 1, lines.size());
            BigDecimal restSum = rest.stream().map(StatementLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            int more = rest.size();
            lines = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
            lines.add(new StatementLine("... and " + more + " more entries (included in the totals above)", restSum));
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("periodLabel", ym.atDay(1).format(DAY) + " - " + ym.atEndOfMonth().format(DAY));
        extra.put("orders", orders);
        extra.put("earnings", earnings.longValue());
        extra.put("refundReversals", reversals.longValue());
        extra.put("adjustments", adjustments.longValue());
        extra.put("debtRepayments", debt.longValue());
        extra.put("payoutsPaid", paidOut.longValue());
        extra.put("platformShare", platformShare.longValue());
        String period = ym.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return issueService.issueStatement(instructor, period, net, extra, lines);
    }
}
