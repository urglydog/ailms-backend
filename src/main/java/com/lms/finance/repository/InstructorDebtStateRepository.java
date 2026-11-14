package com.lms.finance.repository;

import com.lms.finance.entity.InstructorDebtState;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorDebtStateRepository extends JpaRepository<InstructorDebtState, Long> {

    /** Giảng viên nợ từ trước mốc này (dùng để tìm người đã vào bậc hạn chế/ngừng bán). */
    @Query("SELECT s.instructorId FROM InstructorDebtState s WHERE s.debtSince <= :before")
    List<Long> findInstructorIdsInDebtBefore(@Param("before") LocalDateTime before);
}
