package com.lms.report.repository;

import com.lms.report.entity.ReportSnapshot;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<ReportSnapshot> findAllByOrderByIdDesc(Pageable pageable);
}
