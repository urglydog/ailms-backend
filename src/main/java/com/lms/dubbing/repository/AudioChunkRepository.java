package com.lms.dubbing.repository;

import com.lms.dubbing.entity.AudioChunk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link AudioChunk}.
 */
@Repository
public interface AudioChunkRepository extends JpaRepository<AudioChunk, Long> {

    /** Upsert theo callback cua AI Worker — moi (track, chunkIndex) chi 1 dong. */
    Optional<AudioChunk> findByAudioTrack_IdAndChunkIndex(Long audioTrackId, Integer chunkIndex);

    /** UC16/17 — danh sach chunk theo dung thu tu de FE phat playlist khi TrackStatus=PARTIAL. */
    List<AudioChunk> findByAudioTrack_IdOrderByChunkIndexAsc(Long audioTrackId);
}
