package com.lms.dubbing.repository;

import com.lms.dubbing.entity.Transcript;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Transcript}.
 */
@Repository
public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    /** BR-DUB-01 — bản gốc do STT sinh ra, chỉ tồn tại NHIỀU NHẤT 1 bản/bài học. */
    Optional<Transcript> findByLesson_IdAndIsSourceTrue(Long lessonId);

    Optional<Transcript> findByLesson_IdAndLanguage(Long lessonId, String language);
}
