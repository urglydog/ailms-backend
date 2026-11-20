package com.lms.invoice.controller;

import com.lms.invoice.dto.InvoiceDto;
import com.lms.invoice.service.InvoiceQueryService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chứng từ của chính người dùng đăng nhập (học viên: biên lai/phiếu hoàn; giảng viên: phiếu chi/biên lai công nợ, và biên lai nếu có mua khóa). */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
public class InvoiceController {

    private final InvoiceQueryService queryService;

    @GetMapping
    public ResponseEntity<List<InvoiceDto.Res>> mine(Principal principal) {
        return ResponseEntity.ok(queryService.listMine(principal.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceDto.Detail> detail(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(queryService.detail(principal.getName(), id));
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
