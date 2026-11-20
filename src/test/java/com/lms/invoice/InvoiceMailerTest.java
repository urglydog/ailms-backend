package com.lms.invoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.service.EmailService;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.service.InvoiceMailer;
import com.lms.invoice.service.InvoiceQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceMailerTest {

    @Mock private InvoiceQueryService queryService;
    @Mock private EmailService emailService;
    @InjectMocks private InvoiceMailer mailer;

    private static InvoiceQueryService.MailPayload payload(InvoiceType type, LocalDateTime issuedAt) {
        return new InvoiceQueryService.MailPayload("hv@gmail.com", "Học Viên", type, type.prefix() + "-202611-000001",
                new BigDecimal("50000"), issuedAt, "x.pdf", new byte[] {1, 2, 3});
    }

    @Test
    void sendIfRecent_sendsAttachmentForNewInvoice() {
        when(queryService.mailPayload(1L)).thenReturn(payload(InvoiceType.RECEIPT, LocalDateTime.now().minusMinutes(3)));

        mailer.sendIfRecent(1L);

        verify(emailService).sendDocumentEmail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendIfRecent_skipsBackfilledOldInvoices() {
        when(queryService.mailPayload(2L)).thenReturn(payload(InvoiceType.RECEIPT, LocalDateTime.now().minusDays(20)));

        mailer.sendIfRecent(2L);

        verify(emailService, never()).sendDocumentEmail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendIfRecent_neverThrowsWhenLoadingFails() {
        when(queryService.mailPayload(3L)).thenThrow(new IllegalStateException("boom"));

        mailer.sendIfRecent(3L);
    }
}
