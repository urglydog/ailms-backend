package com.lms.invoice.controller;

import com.lms.invoice.dto.InvoiceDto;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.service.InvoiceQueryService;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý chứng từ toàn nền tảng (admin): lọc theo loại/kỳ/người, tải PDF, xuất CSV. */
@RestController
@RequestMapping("/api/v1/admin/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceController {

    private final InvoiceQueryService queryService;

    @GetMapping
    public ResponseEntity<InvoiceDto.AdminPage> list(
            @RequestParam(required = false) InvoiceType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(queryService.search(type, from, to, q, page, size));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) InvoiceType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        String csv = queryService.exportCsv(type, from, to, q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(Principal principal, @PathVariable Long id) {
        InvoiceQueryService.PdfFile file = queryService.pdf(principal.getName(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.bytes());
    }
}
