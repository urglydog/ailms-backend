package com.lms.live.repository;

import com.lms.live.entity.LiveLanguageTrack;
import com.lms.live.enums.LiveTrackStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveLanguageTrackRepository extends JpaRepository<LiveLanguageTrack, Long> {

    Optional<LiveLanguageTrack> findByLiveSession_IdAndTargetLanguageAndActiveFlag(
            Long liveSessionId, String targetLanguage, Integer activeFlag);

    List<LiveLanguageTrack> findByLiveSession_IdAndStatus(Long liveSessionId, LiveTrackStatus status);
}
