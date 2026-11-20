package com.lms.invoice.repository;

import com.lms.invoice.entity.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoice_IdOrderByLineNoAsc(Long invoiceId);
}
