package com.lms.dubbing.repository;

import com.lms.dubbing.entity.TranscriptSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link TranscriptSegment}.
 */
@Repository
public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    /** Dùng để tái sử dụng bản ghi âm gốc khi lồng tiếng sang ngôn ngữ khác cho cùng bài học. */
    List<TranscriptSegment> findByTranscript_IdOrderBySeqAsc(Long transcriptId);
}
