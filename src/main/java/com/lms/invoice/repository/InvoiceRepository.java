package com.lms.invoice.repository;

import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByTypeAndSourceKey(InvoiceType type, String sourceKey);

    boolean existsByTypeAndSourceKey(InvoiceType type, String sourceKey);

    boolean existsByTypeAndSourceId(InvoiceType type, Long sourceId);

    /** Tra chứng từ theo mã truy cập (chỉ dùng cho liên kết/QR công khai). Cột có chỉ mục duy nhất. */
    Optional<Invoice> findByAccessToken(String accessToken);

    /** Chứng từ chưa có mã truy cập (cũ) — job bù cấp mã theo lô nhỏ. */
    List<Invoice> findByAccessTokenIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"party", "relatedInvoice"})
    List<Invoice> findByParty_IdOrderByIssuedAtDescIdDesc(Long partyId, Pageable pageable);

    /** Danh sách cho admin; mọi bộ lọc đều tùy chọn. {@code q} khớp số chứng từ hoặc email/tên người nhận. */
    @EntityGraph(attributePaths = {"party", "relatedInvoice"})
    @Query("SELECT i FROM Invoice i JOIN i.party u WHERE (:type IS NULL OR i.type = :type) "
            + "AND (:from IS NULL OR i.issuedAt >= :from) AND (:to IS NULL OR i.issuedAt < :to) "
            + "AND (:q IS NULL OR LOWER(i.number) LIKE :q OR LOWER(i.sourceKey) LIKE :q OR LOWER(i.snapshotJson) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(u.fullName) LIKE :q "
            + "OR u.id = :qId OR EXISTS (SELECT 1 FROM InvoiceLine l WHERE l.invoice = i AND LOWER(l.description) LIKE :q)) "
            + "ORDER BY i.issuedAt DESC, i.id DESC")
    Page<Invoice> search(@Param("type") InvoiceType type, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                         @Param("q") String q, @Param("qId") long qId, Pageable pageable);
}
