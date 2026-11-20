package com.lms.report.service;

import com.lms.common.service.SystemConfigService;
import com.lms.report.service.FinanceReport.Bucket;
import com.lms.report.service.FinanceReport.CourseRow;
import com.lms.report.service.FinanceReport.Granularity;
import com.lms.report.service.FinanceReport.InstructorRow;
import com.lms.report.service.FinanceReport.Reconciliation;
import com.lms.report.service.FinanceReport.Summary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy vấn tổng hợp cho thống kê tài chính (SQL thuần để gom nhóm gọn trên MySQL; mọi giá trị đi qua tham số, chuỗi ghép vào SQL chỉ lấy
 * từ enum {@link Granularity}). Tài khoản demo (đuôi email cấu hình {@code INVOICE_EXCLUDED_EMAIL_SUFFIX}, đơn {@code DEMO-}) bị loại khỏi mọi số liệu.
 * Khoảng thời gian nửa mở {@code [from, to)}.
 */
@Component
public class FinanceReportQueries {

    static final String CFG_PAYOS_FEE = "PAYOS_PAYOUT_FEE";
    static final long DEFAULT_PAYOS_FEE = 2950L;
    private static final String MATCH_NOTHING = "%\u0001";

    @PersistenceContext
    private EntityManager em;

    private final SystemConfigService config;

    public FinanceReportQueries(SystemConfigService config) {
        this.config = config;
    }

    String excludedEmailPattern() {
        String suffix = config.getString("INVOICE_EXCLUDED_EMAIL_SUFFIX", "@example.com");
        return suffix == null || suffix.isBlank() ? MATCH_NOTHING : "%" + suffix.trim().toLowerCase(Locale.ROOT);
    }

    public long payosFee() {
        return config.getInt(CFG_PAYOS_FEE, (int) DEFAULT_PAYOS_FEE);
    }

    private Query q(String sql, LocalDateTime from, LocalDateTime to) {
        Query query = em.createNativeQuery(sql);
        if (sql.contains(":from")) {
            query.setParameter("from", from);
        }
        if (sql.contains(":to")) {
            query.setParameter("to", to);
        }
        if (sql.contains(":ex")) {
            query.setParameter("ex", excludedEmailPattern());
        }
        return query;
    }

    private static long l(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query q) {
        return (List<Object[]>) q.getResultList();
    }

    private static final String PAY_BASE = " FROM payments p JOIN users u ON u.id = p.user_id WHERE p.status IN ('PAID','REFUNDED') AND p.amount > 0 "
            + "AND p.paid_at >= :from AND p.paid_at < :to AND p.txn_ref NOT LIKE 'DEMO-%' AND LOWER(u.email) NOT LIKE :ex";
    private static final String REFUND_BASE = " FROM payment_refunds r JOIN payments p ON p.id = r.payment_id JOIN users u ON u.id = p.user_id "
            + "WHERE r.approved_at >= :from AND r.approved_at < :to AND p.txn_ref NOT LIKE 'DEMO-%' AND LOWER(u.email) NOT LIKE :ex";
    private static final String PAYOUT_BASE = " FROM instructor_payouts o JOIN users u ON u.id = o.instructor_id WHERE o.status = 'PAID' "
            + "AND o.paid_at >= :from AND o.paid_at < :to AND LOWER(u.email) NOT LIKE :ex";

    @Transactional(readOnly = true)
    public Summary summary(LocalDateTime from, LocalDateTime to) {
        Object[] pay = rows(q("SELECT COUNT(*), COALESCE(SUM(p.amount),0), SUM(CASE WHEN p.status='PAID' THEN p.platform_fee ELSE 0 END), "
                + "SUM(CASE WHEN p.status='PAID' THEN p.instructor_earning ELSE 0 END)" + PAY_BASE, from, to)).get(0);
        Object[] ref = rows(q("SELECT COUNT(*), COALESCE(SUM(r.amount),0)" + REFUND_BASE, from, to)).get(0);
        Object[] out = rows(q("SELECT COUNT(*), COALESCE(SUM(o.amount),0)" + PAYOUT_BASE, from, to)).get(0);
        long payosOrders = l(q("SELECT COUNT(*) FROM payos_payout_orders WHERE status = 'SUCCEEDED' AND completed_at >= :from AND completed_at < :to",
                from, to).getSingleResult());
        long orders = l(pay[0]);
        long gross = l(pay[1]);
        long refunds = l(ref[1]);
        double rate = orders == 0 ? 0 : Math.round(l(ref[0]) * 1000.0 / orders) / 10.0;
        long pending = l(q("SELECT COALESCE(SUM(o.amount),0) FROM instructor_payouts o JOIN users u ON u.id = o.instructor_id "
                + "WHERE o.status IN ('REQUESTED','APPROVED') AND LOWER(u.email) NOT LIKE :ex", from, to).getSingleResult());
        Object[] bal = rows(q("SELECT COALESCE(SUM(GREATEST(t.bal,0)),0), COALESCE(SUM(GREATEST(-t.bal,0)),0) FROM "
                + "(SELECT e.instructor_id, SUM(e.amount) AS bal FROM instructor_ledger_entries e JOIN users u ON u.id = e.instructor_id "
                + "WHERE LOWER(u.email) NOT LIKE :ex GROUP BY e.instructor_id) t", from, to)).get(0);
        long fee = payosFee();
        return new Summary(orders, gross, l(ref[0]), refunds, rate, gross - refunds, l(pay[2]), l(pay[3]), l(out[0]), l(out[1]), payosOrders,
                payosOrders * fee, pending, l(bal[0]), l(bal[1]));
    }

    @Transactional(readOnly = true)
    public List<Bucket> buckets(LocalDateTime from, LocalDateTime to, Granularity g) {
        String f = g.sqlFormat();
        Map<String, long[]> acc = new TreeMap<>();
        for (Object[] r : rows(q("SELECT DATE_FORMAT(p.paid_at, '" + f + "'), COUNT(*), COALESCE(SUM(p.amount),0), "
                + "SUM(CASE WHEN p.status='PAID' THEN p.platform_fee ELSE 0 END), SUM(CASE WHEN p.status='PAID' THEN p.instructor_earning ELSE 0 END)"
                + PAY_BASE + " GROUP BY 1", from, to))) {
            long[] a = acc.computeIfAbsent(s(r[0]), k -> new long[5]);
            a[0] = l(r[1]); a[1] = l(r[2]); a[3] = l(r[3]); a[4] = l(r[4]);
        }
        for (Object[] r : rows(q("SELECT DATE_FORMAT(r.approved_at, '" + f + "'), COALESCE(SUM(r.amount),0)" + REFUND_BASE + " GROUP BY 1", from, to))) {
            acc.computeIfAbsent(s(r[0]), k -> new long[5])[2] = l(r[1]);
        }
        for (Object[] r : rows(q("SELECT DATE_FORMAT(o.paid_at, '" + f + "'), COALESCE(SUM(o.amount),0)" + PAYOUT_BASE + " GROUP BY 1", from, to))) {
            long[] a = acc.computeIfAbsent(s(r[0]), k -> new long[6]);
            if (a.length < 6) {
                long[] b = new long[6];
                System.arraycopy(a, 0, b, 0, a.length);
                acc.put(s(r[0]), b);
                a = b;
            }
            a[5] = l(r[1]);
        }
        return acc.entrySet().stream().map(e -> {
            long[] a = e.getValue();
            return new Bucket(e.getKey(), a[0], a[1], a[2], a[3], a[4], a.length > 5 ? a[5] : 0);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseRow> courses(LocalDateTime from, LocalDateTime to, int limit) {
        return rows(q("SELECT c.id, c.title, i.full_name, COUNT(*), COALESCE(SUM(p.amount),0), "
                + "SUM(CASE WHEN p.status='PAID' THEN p.platform_fee ELSE 0 END), SUM(CASE WHEN p.status='PAID' THEN p.instructor_earning ELSE 0 END), "
                + "SUM(CASE WHEN p.status='REFUNDED' THEN 1 ELSE 0 END)"
                + " FROM payments p JOIN users u ON u.id = p.user_id JOIN courses c ON c.id = p.course_id JOIN users i ON i.id = c.instructor_id "
                + "WHERE p.status IN ('PAID','REFUNDED') AND p.amount > 0 AND p.paid_at >= :from AND p.paid_at < :to AND p.txn_ref NOT LIKE 'DEMO-%' "
                + "AND LOWER(u.email) NOT LIKE :ex GROUP BY c.id, c.title, i.full_name ORDER BY SUM(p.amount) DESC LIMIT " + Math.max(1, Math.min(limit, 500)),
                from, to)).stream()
                .map(r -> new CourseRow(l(r[0]), s(r[1]), s(r[2]), l(r[3]), l(r[4]), l(r[5]), l(r[6]), l(r[7]))).toList();
    }

    @Transactional(readOnly = true)
    public List<InstructorRow> instructors(LocalDateTime from, LocalDateTime to, int limit) {
        return rows(q("SELECT i.id, i.full_name, i.email, COUNT(*), COALESCE(SUM(p.amount),0), "
                + "SUM(CASE WHEN p.status='PAID' THEN p.instructor_earning ELSE 0 END), "
                + "(SELECT COALESCE(SUM(o.amount),0) FROM instructor_payouts o WHERE o.instructor_id = i.id AND o.status='PAID' AND o.paid_at >= :from AND o.paid_at < :to)"
                + " FROM payments p JOIN users u ON u.id = p.user_id JOIN courses c ON c.id = p.course_id JOIN users i ON i.id = c.instructor_id "
                + "WHERE p.status IN ('PAID','REFUNDED') AND p.amount > 0 AND p.paid_at >= :from AND p.paid_at < :to AND p.txn_ref NOT LIKE 'DEMO-%' "
                + "AND LOWER(u.email) NOT LIKE :ex GROUP BY i.id, i.full_name, i.email ORDER BY SUM(p.amount) DESC LIMIT " + Math.max(1, Math.min(limit, 500)),
                from, to)).stream()
                .map(r -> new InstructorRow(l(r[0]), s(r[1]), s(r[2]), l(r[3]), l(r[4]), l(r[5]), l(r[6]))).toList();
    }

    @Transactional(readOnly = true)
    public Reconciliation reconciliation(LocalDateTime from, LocalDateTime to) {
        Object[] payos = rows(q("SELECT COUNT(*), COALESCE(SUM(amount),0) FROM payos_payout_orders WHERE status = 'SUCCEEDED' "
                + "AND completed_at >= :from AND completed_at < :to", from, to)).get(0);
        Object[] ref = rows(q("SELECT COUNT(*), COALESCE(SUM(r.amount),0) FROM payment_refunds r WHERE r.transfer_status = 'TRANSFERRED' "
                + "AND r.transfer_ref LIKE 'PAYOS-%' AND r.transferred_at >= :from AND r.transferred_at < :to", from, to)).get(0);
        Object[] out = rows(q("SELECT COUNT(*), COALESCE(SUM(o.amount),0) FROM instructor_payouts o WHERE o.status = 'PAID' "
                + "AND o.transfer_ref LIKE 'PAYOS-%' AND o.paid_at >= :from AND o.paid_at < :to", from, to)).get(0);
        long outflow = l(payos[1]);
        long recorded = l(ref[1]) + l(out[1]);
        return new Reconciliation(outflow, l(ref[1]), l(out[1]), outflow - recorded, l(payos[0]), l(ref[0]) + l(out[0]));
    }

    /** Chuyển BigDecimal (cột DECIMAL) sang VND nguyên, làm tròn tới đồng. */
    static long vnd(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }
}
