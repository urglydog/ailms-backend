package com.lms.chat.repository;

import com.lms.chat.entity.TutorSecurityPattern;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TutorSecurityPatternRepository extends JpaRepository<TutorSecurityPattern, Long> {
    List<TutorSecurityPattern> findByEnabledTrue();
}
