package com.lms.instructor.repository;

import com.lms.instructor.entity.InstructorVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link InstructorVerification}. */
@Repository
public interface InstructorVerificationRepository extends JpaRepository<InstructorVerification, Long> {

    boolean existsByUser_Id(Long userId);

    /** Chỉ chính chủ tài khoản gọi được — xem docblock {@link InstructorVerification}. */
    Optional<InstructorVerification> findByUser_Id(Long userId);
}
