package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lms.common.ratelimit.RateLimiter;
import com.lms.common.service.SystemConfigService;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.service.InvoiceIssueService;
import com.lms.invoice.service.InvoiceQueryService;
import com.lms.invoice.service.PublicInvoiceService;
import com.lms.invoice.util.InvoiceChecksum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoicePublicLinkTest {

    private static final String GOOD = "A".repeat(43);

    @Mock private InvoiceQueryService queryService;
    @Mock private SystemConfigService config;
    @Mock private RateLimiter rateLimiter;
    @InjectMocks private PublicInvoiceService service;

    @Test
    void newAccessToken_is43Base64UrlCharsAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String t = InvoiceIssueService.newAccessToken();
            assertThat(t).matches("[A-Za-z0-9_-]{43}");
            assertThat(seen.add(t)).isTrue();
        }
    }

    @Test
    void open_returnsThePdfForAValidToken() {
        when(rateLimiter.hit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(0L);
        when(config.getInt("PUBLIC_INVOICE_LINK_ENABLED", 1)).thenReturn(1);
        when(queryService.pdfByAccessToken(GOOD)).thenReturn(Optional.of(new byte[] {1, 2}));

        var r = service.open(GOOD, "1.2.3.4");

        assertThat(r.outcome()).isEqualTo(PublicInvoiceService.Outcome.OK);
        assertThat(r.pdf()).containsExactly(1, 2);
    }

    @Test
    void open_unknownTokenAndMalformedTokenLookIdentical_andMalformedNeverTouchesTheDatabase() {
        when(rateLimiter.hit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(0L);
        when(config.getInt("PUBLIC_INVOICE_LINK_ENABLED", 1)).thenReturn(1);
        when(queryService.pdfByAccessToken(GOOD)).thenReturn(Optional.empty());

        assertThat(service.open(GOOD, "ip").outcome()).isEqualTo(PublicInvoiceService.Outcome.NOT_FOUND);
        for (String bad : new String[] {null, "", "short", "A".repeat(44), "A".repeat(42) + "/", "' OR 1=1 --" + "A".repeat(40), "../../etc/passwd"}) {
            assertThat(service.open(bad, "ip").outcome()).isEqualTo(PublicInvoiceService.Outcome.NOT_FOUND);
        }
        verify(queryService).pdfByAccessToken(GOOD);
        verify(queryService, never()).pdfByAccessToken(org.mockito.ArgumentMatchers.argThat(t -> !GOOD.equals(t)));
    }

    @Test
    void open_killSwitchOffAndRateLimitAreEnforced_failClosedWhenRedisFails() {
        when(rateLimiter.hit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(0L, 5L);
        when(config.getInt("PUBLIC_INVOICE_LINK_ENABLED", 1)).thenReturn(0);

        assertThat(service.open(GOOD, "ip").outcome()).isEqualTo(PublicInvoiceService.Outcome.NOT_FOUND);
        assertThat(service.open(GOOD, "ip").outcome()).isEqualTo(PublicInvoiceService.Outcome.RATE_LIMITED);
        verifyNoInteractions(queryService);
    }

    @Test
    void open_redisFailureDeniesAccess() {
        when(rateLimiter.hit(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.open(GOOD, "ip").outcome()).isEqualTo(PublicInvoiceService.Outcome.RATE_LIMITED);
        verifyNoInteractions(queryService);
    }

    @Test
    void checksum_isStableAndChangesWithAnyContentChange() {
        Invoice inv = new Invoice();
        inv.setNumber("RCP-202611-000001");
        inv.setType(InvoiceType.RECEIPT);
        inv.setIssuedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        inv.setSubtotal(new BigDecimal("100000"));
        inv.setDiscount(BigDecimal.ZERO);
        inv.setTotal(new BigDecimal("100000"));
        inv.setSnapshotJson("{\"partyName\":\"A\"}");
        InvoiceLine l = new InvoiceLine();
        l.setLineNo(1);
        l.setDescription("Java");
        l.setQuantity(1);
        l.setUnitPrice(new BigDecimal("100000"));
        l.setAmount(new BigDecimal("100000"));

        String a = InvoiceChecksum.sha256(inv, List.of(l));
        assertThat(a).hasSize(64).isEqualTo(InvoiceChecksum.sha256(inv, List.of(l)));
        assertThat(InvoiceChecksum.display(a)).matches("[0-9A-F]{4}(-[0-9A-F]{4}){3}");

        l.setAmount(new BigDecimal("100001"));
        assertThat(InvoiceChecksum.sha256(inv, List.of(l))).isNotEqualTo(a);
        l.setAmount(new BigDecimal("100000"));
        inv.setSnapshotJson("{\"partyName\":\"B\"}");
        assertThat(InvoiceChecksum.sha256(inv, List.of(l))).isNotEqualTo(a);
    }
}
