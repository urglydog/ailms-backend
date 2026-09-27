package com.lms.common.repository;

import com.lms.common.entity.AiPromptSecurityFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiPromptSecurityFlagRepository extends JpaRepository<AiPromptSecurityFlag, Long> {
}
