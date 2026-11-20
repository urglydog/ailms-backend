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

    /**
     * Danh sách cho admin (20/11/2026): lọc trạng thái, khoảng ngày tạo, "cần xử lý" (job FAILED mà bài học + ngôn ngữ đó CHƯA có bản lồng tiếng thành công — job lỗi cũ
     * đã được bản sau thay thế thì không còn cần xử lý), tìm theo tên khóa học/bài học/ngôn ngữ/người yêu cầu/ID job. {@code search} là mẫu LIKE chữ thường hoặc null.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT j FROM AiJob j JOIN j.lesson l JOIN l.chapter ch JOIN ch.course c LEFT JOIN j.requestedBy u "
            + "WHERE (:status IS NULL OR j.status = :status) "
            + "AND (:attention = false OR (j.status = com.lms.common.enums.JobStatus.FAILED AND NOT EXISTS (SELECT 1 FROM AiJob o WHERE o.lesson = j.lesson "
            + "AND o.targetLanguage = j.targetLanguage AND o.status = com.lms.common.enums.JobStatus.COMPLETED))) "
            + "AND (:from IS NULL OR j.createdAt >= :from) AND (:to IS NULL OR j.createdAt < :to) "
            + "AND (:search IS NULL OR LOWER(l.title) LIKE :search OR LOWER(c.title) LIKE :search OR LOWER(j.targetLanguage) LIKE :search "
            + "OR LOWER(u.email) LIKE :search OR LOWER(u.fullName) LIKE :search OR j.id = :searchId)")
    Page<AiJob> searchAdmin(
            @org.springframework.data.repository.query.Param("status") JobStatus status,
            @org.springframework.data.repository.query.Param("attention") boolean attention,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("searchId") long searchId,
            Pageable pageable);

    /** Bài học + ngôn ngữ này đã có bản lồng tiếng thành công chưa (chặn thử lại thừa, tốn chi phí). */
    boolean existsByLesson_IdAndTargetLanguageAndStatus(Long lessonId, String targetLanguage, JobStatus status);
}
