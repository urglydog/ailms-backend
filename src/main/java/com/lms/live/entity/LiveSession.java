package com.lms.live.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Phiên Live Classroom (UC50, F11.1) — luôn gắn với 1 khóa học kể cả khi
 * {@link #visibility} là {@code PUBLIC} (BR-LIVE-01).
 *
 * <p>{@link #roomName} là tên phòng LiveKit, UNIQUE, sinh ngẫu nhiên lúc tạo — không đổi
 * được sau đó. {@link #sourceLanguage} khoá cứng khi {@code status = LIVE} (BR-LIVE-04):
 * hiện thực bằng cách đơn giản là KHÔNG có endpoint nào cho sửa field này sau khi tạo, ở
 * bất kỳ trạng thái nào.
 */
@Entity
@Table(name = "live_sessions")
@Getter
@Setter
public class LiveSession extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private LiveVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LiveSessionStatus status = LiveSessionStatus.SCHEDULED;

    @Column(name = "room_name", nullable = false, unique = true, length = 150)
    private String roomName;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    /** Giờ dự kiến bắt đầu — chỉ để hiển thị banner "sắp live", không ràng buộc kỹ thuật. */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * Mốc giảng viên rớt kết nối lần gần nhất (LiveKit webhook ghi/xoá) — chỉ có ý nghĩa khi
     * {@code status = LIVE}. {@code null} nghĩa là đang kết nối bình thường hoặc đã reconnect.
     * BR-LIVE-09.
     */
    @Column(name = "instructor_disconnected_at")
    private LocalDateTime instructorDisconnectedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
