package com.lms.bundle.repository;

import com.lms.bundle.entity.CourseBundle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseBundleRepository extends JpaRepository<CourseBundle, Long> {

    Page<CourseBundle> findByInstructorId(Long instructorId, Pageable pageable);
    
    @Query("SELECT cb FROM CourseBundle cb JOIN cb.courses c WHERE c.id = :courseId AND cb.isActive = true")
    List<CourseBundle> findActiveBundlesByCourseId(@Param("courseId") Long courseId);
    
    @Query("SELECT cb FROM CourseBundle cb LEFT JOIN FETCH cb.courses WHERE cb.id = :id")
    Optional<CourseBundle> findByIdWithCourses(@Param("id") Long id);
}
