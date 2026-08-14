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

    @org.springframework.data.jpa.repository.Query("SELECT ts FROM TranscriptSegment ts JOIN ts.transcript t JOIN t.lesson l JOIN l.chapter c WHERE c.course.id = :courseId AND t.isSource = true ORDER BY l.id ASC, ts.seq ASC")
    List<TranscriptSegment> findByCourseIdAndIsSourceTrue(@org.springframework.data.repository.query.Param("courseId") Long courseId);

    @org.springframework.data.jpa.repository.Query("SELECT ts FROM TranscriptSegment ts JOIN ts.transcript t JOIN t.lesson l WHERE l.chapter.id = :chapterId AND t.isSource = true ORDER BY l.id ASC, ts.seq ASC")
    List<TranscriptSegment> findByChapterIdAndIsSourceTrue(@org.springframework.data.repository.query.Param("chapterId") Long chapterId);
}
