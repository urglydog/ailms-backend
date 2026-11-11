package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.enums.RevenueSource;
import com.lms.finance.dto.ShareDto;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ShareStatsServiceTest {

    private static final String EMAIL = "gv@lms.local";

    @Mock private CourseRepository courseRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private ShareStatsService service;

    private Course course(long id, String slug, CourseStatus status) {
        Course c = new Course();
        c.setId(id);
        c.setTitle("Khóa " + id);
        c.setSlug(slug);
        c.setStatus(status);
        c.setVisibility(CourseVisibility.PUBLIC);
        c.setReferralCode("REF" + id);
        return c;
    }

    private List<Object[]> rows(Object[]... r) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] x : r) {
            list.add(x);
        }
        return list;
    }

    @Test
    void listForInstructor_splitsOrdersBySource_andKeepsCoursesWithoutSales() {
        when(courseRepository.findByInstructor_Email(eq(EMAIL), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                course(1, "unity", CourseStatus.PUBLISHED), course(2, "blender", CourseStatus.DRAFT))));
        when(paymentRepository.aggregateSalesBySource(eq(EMAIL), any(), any())).thenReturn(rows(
                new Object[]{1L, RevenueSource.INSTRUCTOR_REFERRAL, 3L, new BigDecimal("291000")},
                new Object[]{1L, RevenueSource.ORGANIC, 5L, new BigDecimal("185000")}));

        List<ShareDto.CourseShareRes> res = service.listForInstructor(EMAIL, null, null);

        assertThat(res).hasSize(2);
        ShareDto.CourseShareRes unity = res.get(0);
        assertThat(unity.referralCode()).isEqualTo("REF1");
        assertThat(unity.shareable()).isTrue();
        assertThat(unity.referralOrders()).isEqualTo(3);
        assertThat(unity.referralEarning()).isEqualByComparingTo("291000");
        assertThat(unity.organicOrders()).isEqualTo(5);
        assertThat(unity.organicEarning()).isEqualByComparingTo("185000");
        ShareDto.CourseShareRes blender = res.get(1);
        assertThat(blender.shareable()).isFalse(); // chưa xuất bản → chưa có link dùng được
        assertThat(blender.referralOrders()).isZero();
        assertThat(blender.organicEarning()).isEqualByComparingTo("0");
    }

    @Test
    void listForInstructor_dateRangeIsInclusiveOfTheLastDay_andOpenWhenNull() {
        when(courseRepository.findByInstructor_Email(eq(EMAIL), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(paymentRepository.aggregateSalesBySource(eq(EMAIL), any(), any())).thenReturn(rows());

        service.listForInstructor(EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(paymentRepository).aggregateSalesBySource(eq(EMAIL), start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 11, 0, 0)); // [1/11, 11/11) gồm cả ngày 10/11
    }
}
