package com.lms.communication.repository;

import com.lms.communication.entity.AssignmentSubmission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    @EntityGraph(attributePaths = {"student"})
    List<AssignmentSubmission> findByAssignment_IdOrderBySubmittedAtDesc(Long assignmentId);

    Optional<AssignmentSubmission> findByAssignment_IdAndStudent_Id(Long assignmentId, Long studentId);

    long countByAssignment_Id(Long assignmentId);

    long countByAssignment_IdAndScoreIsNotNull(Long assignmentId);
}
