package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceLineRepository;
import com.lms.invoice.repository.InvoiceRepository;
import com.lms.invoice.service.InvoiceQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceQueryServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository lineRepository;
    @Mock private UserRepository userRepository;

    private InvoiceQueryService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new InvoiceQueryService(invoiceRepository, lineRepository, userRepository, new ObjectMapper());
        User owner = user(1L, "owner@gmail.com", Role.STUDENT);
        invoice = new Invoice();
        invoice.setId(50L);
        invoice.setNumber("RCP-202611-000001");
        invoice.setType(InvoiceType.RECEIPT);
        invoice.setParty(owner);
        invoice.setSubtotal(new BigDecimal("1000"));
        invoice.setTotal(new BigDecimal("1000"));
        invoice.setIssuedAt(LocalDateTime.of(2026, 11, 1, 0, 0));
        invoice.setSnapshotJson("{\"partyName\":\"Owner\"}");
    }

    private static User user(long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("U" + id);
        u.setRole(role);
        return u;
    }

    @Test
    void pdf_ownerCanDownload() {
        when(userRepository.findByEmail("owner@gmail.com")).thenReturn(Optional.of(invoice.getParty()));
        when(invoiceRepository.findById(50L)).thenReturn(Optional.of(invoice));
        when(lineRepository.findByInvoice_IdOrderByLineNoAsc(50L)).thenReturn(List.of());

        InvoiceQueryService.PdfFile file = service.pdf("owner@gmail.com", 50L);

        assertThat(file.filename()).isEqualTo("RCP-202611-000001.pdf");
        assertThat(file.bytes()).isNotEmpty();
    }

    @Test
    void pdf_otherUserGetsNotFoundNotForbidden() {
        when(userRepository.findByEmail("other@gmail.com")).thenReturn(Optional.of(user(2L, "other@gmail.com", Role.STUDENT)));
        when(invoiceRepository.findById(50L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.pdf("other@gmail.com", 50L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pdf_adminCanDownloadAnyInvoice() {
        when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.of(user(9L, "admin@lms.local", Role.ADMIN)));
        when(invoiceRepository.findById(50L)).thenReturn(Optional.of(invoice));
        when(lineRepository.findByInvoice_IdOrderByLineNoAsc(50L)).thenReturn(List.of());

        assertThat(service.pdf("admin@lms.local", 50L).bytes()).isNotEmpty();
    }

    @Test
    void csv_neutralisesSpreadsheetFormulasAndEscapesQuotes() {
        assertThat(InvoiceQueryService.csv("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(InvoiceQueryService.csv("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(InvoiceQueryService.csv(null)).isEqualTo("\"\"");
    }
}
