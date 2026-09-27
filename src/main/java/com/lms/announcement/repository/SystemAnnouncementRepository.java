package com.lms.announcement.repository;

import com.lms.announcement.entity.SystemAnnouncement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository cho {@link SystemAnnouncement}. */
@Repository
public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Long> {

    /** Banner đầu trang (BR ngầm định — chỉ severity HIGH) — mới nhất trong số các thông báo
     * CHƯA hết hạn (hoặc không có hạn). Không lọc theo audience: banner "Bảo trì hệ thống" hiển
     * thị cho MỌI người xem trang, kể cả khi audience gốc chỉ nhắm Giảng viên/Học viên — mức độ
     * HIGH ưu tiên hiển thị rộng hơn phạm vi gửi thông báo trong app. */
    @Query("SELECT a FROM SystemAnnouncement a WHERE a.severity = com.lms.common.enums.AnnouncementSeverity.HIGH "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.createdAt DESC")
    Optional<SystemAnnouncement> findLatestActiveHighSeverity(@Param("now") LocalDateTime now);

    /** Lịch sử cho Admin xem lại đã gửi những gì, trạng thái phát tán tới đâu. */
    List<SystemAnnouncement> findAllByOrderByCreatedAtDesc();
}
