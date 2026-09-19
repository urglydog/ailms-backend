package com.lms.instructor.repository;

import com.lms.instructor.entity.InstructorVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorVerificationRepository extends JpaRepository<InstructorVerification, Long> {

    /** BR-VERIFY-01 — dùng để chặn/không chặn gửi khóa học đi duyệt (xem {@code CourseService}). */
    boolean existsByUser_Id(Long userId);

    Optional<InstructorVerification> findByUser_Id(Long userId);
}
