package com.lms.auth.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import com.lms.auth.entity.UserStreak;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStreakRepository extends JpaRepository<UserStreak, Long> {

    /**
     * Streak CÓ THỂ cần nhắc (hiệu năng đợt 3): còn chuỗi và học gần đây (lastActivityDate ≥ minActivityDate).
     * Lọc thô trong DB để job mỗi giờ không phải nạp cả bảng; điều kiện chính xác theo múi giờ của từng
     * học viên vẫn kiểm ở {@code StreakReminderJob}. Đọc theo lô (Slice), sắp theo userId để lật trang ổn định.
     */
    @Query("SELECT s FROM UserStreak s WHERE s.currentStreak > 0 AND s.lastActivityDate >= :minActivityDate "
            + "ORDER BY s.userId")
    Slice<UserStreak> findReminderCandidates(@Param("minActivityDate") LocalDate minActivityDate, Pageable pageable);
}
