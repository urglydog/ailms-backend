package com.lms.catalog.repository;

import com.lms.catalog.entity.CourseActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseActivityLogRepository extends JpaRepository<CourseActivityLog, Long> {
    List<CourseActivityLog> findByCourse_IdOrderByCreatedAtDesc(Long courseId, Pageable pageable);
}
