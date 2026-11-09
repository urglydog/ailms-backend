package com.lms.auth.repository;

import com.lms.auth.entity.UserLearningDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface UserLearningDayRepository extends JpaRepository<UserLearningDay, Long> {
    boolean existsByUser_IdAndActivityDate(Long userId, LocalDate activityDate);

    List<UserLearningDay> findByUser_IdAndActivityDateBetweenOrderByActivityDateDesc(Long userId, LocalDate startDate, LocalDate endDate);
}
