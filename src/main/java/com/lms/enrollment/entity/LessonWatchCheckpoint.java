package com.lms.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Sprint 3 mục 10 — Retention Heatmap (Drop-off Rate). 1 dòng = 1 học viên đã xem TỚI (ít nhất)
 * mốc {@code decile} (1..10, ứng 10%/20%/.../100% thời lượng) của 1 bài học, ghi ĐÚNG 1 LẦN nhờ
 * UNIQUE(lesson_id, user_id, decile) — xem {@link com.lms.enrollment.repository.LessonWatchCheckpointRepository#upsertCheckpoint}.
 *
 * <p>Không extend {@code BaseEntity}: bảng này chỉ được GHI qua native query ({@code INSERT
 * IGNORE}, xem lý do ở repository) và ĐỌC qua aggregate query — không đi qua vòng đời persist
 * chuẩn của JPA nên không cần cột audit {@code created_at}/{@code updated_at} của BaseEntity.
 */
@Entity
@Table(name = "lesson_watch_checkpoints")
@Getter
@Setter
public class LessonWatchCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // columnDefinition bắt buộc: cột DB là TINYINT (migration V137, đủ chứa 1..10) nhưng
    // Integer mặc định map sang INTEGER — lệch kiểu khiến Hibernate schema-validate ném
    // SchemaManagementException mỗi lần khởi động (bug thật gây restart loop, 29/09/2026).
    @Column(name = "decile", nullable = false, columnDefinition = "TINYINT")
    private Integer decile;

    @Column(name = "reached_at", nullable = false)
    private LocalDateTime reachedAt;
}
