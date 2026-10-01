package com.lms.material.repository;

import com.lms.material.entity.StudentStudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentStudyPlanRepository extends JpaRepository<StudentStudyPlan, Long> {
    Optional<StudentStudyPlan> findByUserIdAndCourseId(Long userId, Long courseId);
    void deleteByUserIdAndCourseId(Long userId, Long courseId);
}
