package com.lms.material.repository;

import com.lms.material.entity.StudentStudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface StudentStudyPlanRepository extends JpaRepository<StudentStudyPlan, Long> {
    Optional<StudentStudyPlan> findByUserIdAndCourseId(Long userId, Long courseId);

    @Modifying
    @Transactional
    @Query("DELETE FROM StudentStudyPlan p WHERE p.userId = :userId AND p.courseId = :courseId")
    void deleteByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
