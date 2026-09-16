package com.lms.material.repository;

import com.lms.material.entity.CourseResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseResourceRepository extends JpaRepository<CourseResource, Long> {
    List<CourseResource> findByCourse_IdAndIsDeletedFalseOrderByCreatedAtDesc(Long courseId);
}
