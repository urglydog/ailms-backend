package com.lms.material.repository;

import com.lms.material.entity.MaterialAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialAssignmentRepository extends JpaRepository<MaterialAssignment, Long> {
    List<MaterialAssignment> findByCourse_Id(Long courseId);
    List<MaterialAssignment> findByChapter_Id(Long chapterId);
    List<MaterialAssignment> findByLesson_Id(Long lessonId);
    void deleteByMaterial_Id(Long materialId);
}
