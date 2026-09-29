package com.lms.enrollment.repository;

import com.lms.enrollment.entity.LessonWatchCheckpoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link LessonWatchCheckpoint} (Sprint 3 mục 10 — Retention Heatmap). */
@Repository
public interface LessonWatchCheckpointRepository extends JpaRepository<LessonWatchCheckpoint, Long> {

    /** Ghi 1 checkpoint "đã xem tới decile X" — BẮT BUỘC dùng native query {@code INSERT IGNORE},
     * KHÔNG dùng {@code repository.save(new LessonWatchCheckpoint(...))}: method này chạy CHUNG
     * transaction với việc ghi {@code LessonProgress} mỗi 15s; nếu dùng JPA save thường và đụng
     * đúng UNIQUE(lesson_id, user_id, decile) (2 request gần như đồng thời, hoặc backfill nhiều
     * decile 1 lúc), Hibernate sẽ ném {@code DataIntegrityViolationException} và rollback LUÔN cả
     * việc ghi nhận tiến độ học tập chính — hậu quả nặng hơn nhiều so với thiếu 1 dòng thống kê.
     * {@code INSERT IGNORE} để MySQL tự bỏ qua khi trùng key, không ném lỗi về tầng Java. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO lesson_watch_checkpoints (lesson_id, user_id, decile, reached_at) "
            + "VALUES (:lessonId, :userId, :decile, NOW())", nativeQuery = true)
    void upsertCheckpoint(@Param("lessonId") Long lessonId, @Param("userId") Long userId, @Param("decile") int decile);

    /** Toàn bộ decile ĐANG CÓ dữ liệu của 1 bài học, kèm số học viên KHÁC NHAU đã đạt tới — 1
     * query duy nhất cho cả 10 decile (thay vì gọi 10 lần). Trả từng dòng [decile, count]; decile
     * nào KHÔNG có ai đạt tới thì không xuất hiện trong kết quả — tầng Service tự điền 0 cho các
     * decile thiếu. */
    @Query("SELECT c.decile, COUNT(DISTINCT c.userId) FROM LessonWatchCheckpoint c "
            + "WHERE c.lessonId = :lessonId GROUP BY c.decile")
    List<Object[]> countDistinctUsersPerDecile(@Param("lessonId") Long lessonId);
}
