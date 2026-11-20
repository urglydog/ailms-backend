package com.lms.report.service;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo tài chính của một kỳ (đơn vị tiền: VND, số nguyên). Bản chụp JSON của {@link Summary} được lưu ở report_snapshots. */
public record FinanceReport(LocalDate from, LocalDate to, Granularity granularity, Summary summary, List<Bucket> buckets,
                            List<CourseRow> courses, List<InstructorRow> instructors, Reconciliation reconciliation) {

    public enum Granularity {
        DAY("%Y-%m-%d"), WEEK("%x-W%v"), MONTH("%Y-%m");

        private final String sqlFormat;

        Granularity(String sqlFormat) {
            this.sqlFormat = sqlFormat;
        }

        public String sqlFormat() {
            return sqlFormat;
        }
    }

    /**
     * {@code grossRevenue}: tổng tiền học viên đã trả cho đơn thanh toán trong kỳ (kể cả đơn sau đó bị hoàn); {@code refunds}: các khoản hoàn được duyệt trong kỳ;
     * {@code platformFee}/{@code instructorEarning}: phần chia của các đơn còn hiệu lực (chưa hoàn); {@code payoutsPaid}: rút tiền đã chi trong kỳ;
     * {@code payosFee}: số lệnh chi PayOS thành công trong kỳ nhân phí cấu hình; các mục "tại thời điểm xem" không phụ thuộc kỳ.
     */
    public record Summary(long orders, long grossRevenue, long refundCount, long refunds, double refundRatePct, long netRevenue, long platformFee,
                          long instructorEarning, long payoutsPaidCount, long payoutsPaid, long payosOrders, long payosFee,
                          long pendingPayouts, long obligations, long receivables) {}

    public record Bucket(String period, long orders, long grossRevenue, long refunds, long platformFee, long instructorEarning, long payoutsPaid) {}

    public record CourseRow(long courseId, String title, String instructorName, long orders, long grossRevenue, long platformFee,
                            long instructorEarning, long refundedOrders) {}

    public record InstructorRow(long instructorId, String name, String email, long orders, long grossRevenue, long instructorEarning, long payoutsPaid) {}

    /** Đối soát chi PayOS: tổng lệnh chi PayOS thành công so với các khoản hoàn/rút đã ghi nhận chuyển bằng PayOS trong kỳ. */
    public record Reconciliation(long payosOutflow, long recordedRefunds, long recordedPayouts, long difference, long payosOrders, long recordedCount) {}
}
