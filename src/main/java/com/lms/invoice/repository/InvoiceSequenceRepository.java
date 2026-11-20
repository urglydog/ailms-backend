package com.lms.invoice.repository;

import com.lms.invoice.entity.InvoiceSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.seqKey = :key")
    Optional<InvoiceSequence> lockByKey(@Param("key") String key);

    /** Tạo dòng bộ đếm nếu chưa có; hai luồng cùng tạo thì một luồng bị bỏ qua êm (INSERT IGNORE), sau đó cả hai đọc lại với khóa. */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT IGNORE INTO invoice_sequences (seq_key, seq_value) VALUES (:key, 0)")
    void createIfAbsent(@Param("key") String key);
}
