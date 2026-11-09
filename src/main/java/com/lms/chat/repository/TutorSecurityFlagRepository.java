package com.lms.chat.repository;

import com.lms.chat.entity.TutorSecurityFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TutorSecurityFlagRepository extends JpaRepository<TutorSecurityFlag, Long> {

    /** {@code @EntityGraph} nạp sẵn student + course — tránh N+1 khi map sang DTO cho Admin. */
    @EntityGraph(attributePaths = {"student", "course"})
    Page<TutorSecurityFlag> findAllBy(Pageable pageable);
}
