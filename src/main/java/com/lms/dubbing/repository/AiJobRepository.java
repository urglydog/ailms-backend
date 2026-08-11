package com.lms.dubbing.repository;

import com.lms.common.enums.JobStatus;
import com.lms.dubbing.entity.AiJob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link AiJob}.
 */
@Repository
public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    /** BR-DUB-05 — tim job dang chay (activeFlag = 1) de chan tao trung. */
    Optional<AiJob> findByLesson_IdAndTargetLanguageAndActiveFlag(Long lessonId, String targetLanguage, Integer activeFlag);

    /** Loc job theo trang thai trong PHAM VI 1 bai hoc. */
    List<AiJob> findByLesson_IdAndStatusIn(Long lessonId, Collection<JobStatus> statuses);

    /** UC45 — Admin giam sat hang doi TOAN HE THONG, phan trang theo trang thai. */
    Page<AiJob> findByStatusIn(Collection<JobStatus> statuses, Pageable pageable);
}
