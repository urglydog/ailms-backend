package com.lms.dubbing.repository;

import com.lms.dubbing.entity.AudioTrack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link AudioTrack}.
 */
@Repository
public interface AudioTrackRepository extends JpaRepository<AudioTrack, Long> {

    /** BR-DUB-04 — tai su dung track da co san thay vi chay lai pipeline. */
    Optional<AudioTrack> findByLesson_IdAndLanguage(Long lessonId, String language);

    /** UC47 — chan xoa 1 VoiceMapping dang duoc AudioTrack nao do tham chieu (FK). */
    boolean existsByVoiceMapping_Id(Long voiceMappingId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT a.language FROM AudioTrack a JOIN a.lesson l JOIN l.chapter c WHERE c.course.id = :courseId AND a.status = com.lms.common.enums.TrackStatus.COMPLETED")
    java.util.List<String> findAvailableLanguagesByCourse(@org.springframework.data.repository.query.Param("courseId") Long courseId);
}
