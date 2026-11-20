package com.lms.notification.repository;

import com.lms.notification.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Notification}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /** Thông báo của một người dùng, bỏ qua các thông báo đã thu hồi, mới nhất trước. */
    List<Notification> findByUser_IdAndHiddenAtIsNullOrderByCreatedAtDesc(Long userId);

    /** Sửa tiêu đề/nội dung của mọi thông báo đã gửi từ một nguồn (thông báo hệ thống được sửa). */
    @Modifying
    @Query("UPDATE Notification n SET n.title = :title, n.content = :content WHERE n.sourceRef = :ref")
    int updateBySource(@Param("ref") String ref, @Param("title") String title, @Param("content") String content);

    /** Thu hồi: ẩn mọi thông báo của một nguồn khỏi người dùng (không xóa). */
    @Modifying
    @Query("UPDATE Notification n SET n.hiddenAt = :now WHERE n.sourceRef = :ref AND n.hiddenAt IS NULL")
    int hideBySource(@Param("ref") String ref, @Param("now") java.time.LocalDateTime now);

    /** (26/09/2026, sửa lỗi) — trước đây "Đánh dấu đã đọc" chỉ đổi state ở FE, không lưu DB nên
     * tải lại trang là mất, badge số chưa đọc không giảm. Điều kiện `userId` trong WHERE (không
     * chỉ lọc bằng repository method riêng) để KHÔNG thể đánh dấu đọc hộ thông báo của người
     * khác — cùng khuôn `MessageRepository.markConversationRead`. */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.user.id = :userId")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);
}
