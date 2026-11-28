package com.lms.chat.repository;

import com.lms.chat.entity.CourseTutorInsight;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTutorInsightRepository extends JpaRepository<CourseTutorInsight, Long> {

    Optional<CourseTutorInsight> findByCourseId(Long courseId);

    List<CourseTutorInsight> findByStatusOrderByUpdatedAtAsc(String status, Pageable pageable);
}
