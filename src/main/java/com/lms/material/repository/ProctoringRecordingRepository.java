package com.lms.material.repository;

import com.lms.material.entity.ProctoringRecording;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProctoringRecordingRepository extends JpaRepository<ProctoringRecording, Long> {
    Optional<ProctoringRecording> findByAttempt_Id(Long attemptId);
}
