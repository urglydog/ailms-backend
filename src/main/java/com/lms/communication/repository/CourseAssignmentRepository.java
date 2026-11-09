package com.lms.communication.repository;

import com.lms.communication.entity.CourseAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseAssignmentRepository extends JpaRepository<CourseAssignment, Long> {

    List<CourseAssignment> findByLesson_IdOrderByIdAsc(Long lessonId);

    List<CourseAssignment> findByLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(String email);

    List<CourseAssignment> findByLesson_Chapter_Course_IdOrderByCreatedAtDesc(Long courseId);
}
