package com.lms.common.repository;

import com.lms.common.entity.AiBillingEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiBillingEntryRepository extends JpaRepository<AiBillingEntry, Long> {

    List<AiBillingEntry> findAllByOrderByPeriodStartDesc();

    /** Các hóa đơn nằm TRỌN trong khoảng đang xem (hóa đơn chỉ chạm một phần khoảng sẽ so sánh sai nên không tính). */
    @Query("SELECT e FROM AiBillingEntry e WHERE e.periodStart >= :from AND e.periodEnd <= :to ORDER BY e.periodStart")
    List<AiBillingEntry> findWithin(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Có hóa đơn nào chồng lấn khoảng ngày này không (để chặn nhập trùng, tránh đếm đôi). */
    @Query("SELECT COUNT(e) FROM AiBillingEntry e WHERE e.periodStart <= :to AND e.periodEnd >= :from")
    long countOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
