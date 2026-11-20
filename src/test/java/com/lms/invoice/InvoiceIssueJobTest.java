package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.invoice.entity.Invoice;
import com.lms.invoice.service.InvoiceIssueService;
import com.lms.invoice.service.InvoiceSourceFinder;
import com.lms.payment.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceIssueJobTest {

    @Mock private InvoiceSourceFinder finder;
    @Mock private InvoiceIssueService issueService;
    @InjectMocks private com.lms.invoice.service.InvoiceIssueJob job;

    @Test
    void run_oneFailingPaymentDoesNotBlockTheOthers() {
        Payment bad = new Payment();
        Payment good = new Payment();
        when(finder.paymentsWithoutReceipt(100)).thenReturn(List.of(bad, good));
        when(finder.refundsWithoutVoucher(100)).thenReturn(List.of());
        when(finder.payoutsWithoutVoucher(100)).thenReturn(List.of());
        when(finder.debtPaymentsWithoutReceipt(100)).thenReturn(List.of());
        when(issueService.issueReceipt(bad)).thenThrow(new IllegalStateException("boom"));
        when(issueService.issueReceipt(good)).thenReturn(Optional.of(new Invoice()));

        job.run();

        verify(issueService).issueReceipt(good);
    }

    @Test
    void run_neverThrowsEvenWhenTheFinderFails() {
        when(finder.paymentsWithoutReceipt(any(Integer.class))).thenThrow(new IllegalStateException("db down"));

        job.run();

        assertThat(true).isTrue();
    }
}
