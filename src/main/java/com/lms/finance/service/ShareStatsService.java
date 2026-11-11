package com.lms.finance.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.RevenueSource;
import com.lms.finance.dto.ShareDto;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang "Chia sẻ & Quảng bá" của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn D): mọi khóa của giảng
 * viên kèm mã giới thiệu riêng và số đơn/thu nhập chia theo nguồn (qua link giới thiệu của chính họ vs tự tìm thấy trên
 * nền tảng). Chỉ đo ĐƠN MUA đã thanh toán (đã đăng nhập, dữ liệu sẵn có trong {@code payments}) — không đo lượt xem/click
 * vì cần API công khai ghi dữ liệu (đã hoãn có chủ đích, xem docs/PAYMENT_PAYOUT_EPIC.md D6). Đơn đã hoàn tiền tự bị loại
 * vì truy vấn chỉ lấy status = PAID.
 */
@Service
@RequiredArgsConstructor
public class ShareStatsService {

    private final CourseRepository courseRepository;
    private final PaymentRepository paymentRepository;

    private record Agg(long orders, BigDecimal earning) {
        static final Agg ZERO = new Agg(0, BigDecimal.ZERO);
    }

    /** {@code from}/{@code to} (bao gồm cả ngày {@code to}) đều null = toàn thời gian. */
    @Transactional(readOnly = true)
    public List<ShareDto.CourseShareRes> listForInstructor(String email, LocalDate from, LocalDate to) {
        LocalDateTime start = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(1) : to.plusDays(1).atStartOfDay();

        Map<Long, Agg> referral = new HashMap<>();
        Map<Long, Agg> organic = new HashMap<>();
        for (Object[] row : paymentRepository.aggregateSalesBySource(email, start, end)) {
            Long courseId = (Long) row[0];
            RevenueSource source = (RevenueSource) row[1];
            Agg agg = new Agg((Long) row[2], (BigDecimal) row[3]);
            (source == RevenueSource.INSTRUCTOR_REFERRAL ? referral : organic).put(courseId, agg);
        }

        return courseRepository.findByInstructor_Email(email, Pageable.unpaged()).getContent().stream()
                .map(c -> toRes(c, referral.getOrDefault(c.getId(), Agg.ZERO), organic.getOrDefault(c.getId(), Agg.ZERO)))
                .toList();
    }

    private static ShareDto.CourseShareRes toRes(Course c, Agg referral, Agg organic) {
        return new ShareDto.CourseShareRes(
                c.getId(), c.getTitle(), c.getSlug(), c.getThumbnailUrl(), c.getStatus(), c.getVisibility(),
                c.getReferralCode(), c.getStatus() == CourseStatus.PUBLISHED,
                referral.orders(), referral.earning(), organic.orders(), organic.earning());
    }
}
