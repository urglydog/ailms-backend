package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.common.enums.PaymentStatus;
import com.lms.payment.repository.PaymentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PaymentAdminSearchTest {

    @Mock private PaymentRepository paymentRepository;
    @InjectMocks private PaymentService service;

    @Test
    void searchPayments_withoutKeywordOrStatusReturnsEmptyPageWithoutQuerying() {
        var page = service.searchPayments("  ", null, 0, 20);

        assertThat(page.items()).isEmpty();
        verify(paymentRepository, never()).searchForAdmin(any(), anyLong(), any(), any());
    }

    @Test
    void searchPayments_normalisesKeywordDetectsNumericIdAndCapsThePageSize() {
        Page<com.lms.payment.entity.Payment> empty = new PageImpl<>(List.of());
        when(paymentRepository.searchForAdmin(any(), anyLong(), any(), any(Pageable.class))).thenReturn(empty);

        service.searchPayments(" 4271 ", null, 2, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).searchForAdmin(eq("%4271%"), eq(4271L), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    void searchPayments_statusOnlyIsAValidSearch() {
        when(paymentRepository.searchForAdmin(any(), anyLong(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.searchPayments(null, PaymentStatus.FAILED, 0, 10);

        verify(paymentRepository).searchForAdmin(isNull(), eq(-1L), eq(PaymentStatus.FAILED), any(Pageable.class));
    }
}
