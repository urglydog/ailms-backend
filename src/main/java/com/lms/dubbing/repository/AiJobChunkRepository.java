package com.lms.dubbing.repository;

import com.lms.dubbing.entity.AiJobChunk;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link AiJobChunk}.
 */
@Repository
public interface AiJobChunkRepository extends JpaRepository<AiJobChunk, Long> {

    /** Upsert theo callback cua AI Worker — moi (job, chunkIndex) chi 1 dong. */
    Optional<AiJobChunk> findByAiJob_IdAndChunkIndex(Long aiJobId, Integer chunkIndex);
}
