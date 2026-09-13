package com.lms.dubbing.repository;

import com.lms.dubbing.entity.Transcript;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Transcript}.
 */
@Repository
public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    /** BR-DUB-01 — bản gốc do STT sinh ra, chỉ tồn tại NHIỀU NHẤT 1 bản/bài học. */
    Optional<Transcript> findByLesson_IdAndIsSourceTrue(Long lessonId);

    Optional<Transcript> findByLesson_IdAndLanguage(Long lessonId, String language);

    /** BR-MAT-01 — điều kiện DUY NHẤT để cho phép sinh học liệu: khoá học có ít nhất 1 bài đã có
     * transcript gốc, KHÔNG phụ thuộc đã lồng tiếng ngôn ngữ nào hay chưa. */
    @Query("SELECT COUNT(t) > 0 FROM Transcript t JOIN t.lesson l JOIN l.chapter c "
            + "WHERE c.course.id = :courseId AND t.isSource = true")
    boolean existsSourceTranscriptByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(t) > 0 FROM Transcript t JOIN t.lesson l "
            + "WHERE l.chapter.id = :chapterId AND t.isSource = true")
    boolean existsSourceTranscriptByChapterId(@Param("chapterId") Long chapterId);

    @Query("SELECT COUNT(t) > 0 FROM Transcript t JOIN t.lesson l "
            + "WHERE l.id IN :lessonIds AND t.isSource = true")
    boolean existsSourceTranscriptByLessonIdIn(@Param("lessonIds") List<Long> lessonIds);

    /** "Danh sách bài trong khoá đã sẵn sàng sinh học liệu" cho UI chọn phạm vi Chương/Bài tuỳ chọn. */
    @Query("SELECT l.id FROM Transcript t JOIN t.lesson l JOIN l.chapter c "
            + "WHERE c.course.id = :courseId AND t.isSource = true")
    List<Long> findLessonIdsWithSourceTranscriptByCourseId(@Param("courseId") Long courseId);

    /** Ngôn ngữ đã có bản dịch sẵn (do lồng tiếng HOẶC sinh học liệu tạo trước) — chỉ dùng làm
     * gợi ý hiển thị dấu tích ở FE (BR-MAT-01 không hạn chế ngôn ngữ theo dữ liệu này). */
    @Query("SELECT DISTINCT t.language FROM Transcript t JOIN t.lesson l JOIN l.chapter c "
            + "WHERE c.course.id = :courseId AND t.isSource = false")
    List<String> findTranslatedLanguagesByCourseId(@Param("courseId") Long courseId);
}
