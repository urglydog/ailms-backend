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

    /** UC45 (23/10/2026, mo rong) — tim theo ten bai hoc/ngon ngu dich, ket hop optional status,
     * phan trang server-side thay vi chi loc theo status nhu truoc. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT j FROM AiJob j WHERE (:status IS NULL OR j.status = :status) "
            + "AND (:search IS NULL OR LOWER(j.lesson.title) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(j.targetLanguage) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AiJob> search(
            @org.springframework.data.repository.query.Param("status") JobStatus status,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable);
}
