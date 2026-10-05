package com.lms.auth.repository;

import com.lms.auth.entity.User;
import com.lms.common.enums.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link User}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    java.util.Optional<User> findByEmail(String email);

    /** Auto-ban bằng AI (25/09/2026) — danh sách đề xuất khoá đang chờ Admin xử lý. */
    java.util.List<User> findByAiLockProposedAtIsNotNull();

    /** Thông báo hệ thống từ Admin (26/09/2026) — phạm vi INSTRUCTOR/STUDENT. */
    java.util.List<User> findByRole(Role role);

    /** Ranking cộng đồng (XpService) — UPDATE atomic, KHÔNG load-modify-save, để 2 hành động
     * cộng XP cùng lúc của 1 user (hiếm nhưng có thể) không ghi đè lẫn nhau. */
    @Modifying
    @Query("UPDATE User u SET u.totalXp = u.totalXp + :amount WHERE u.id = :userId")
    int addXp(@Param("userId") Long userId, @Param("amount") long amount);

    /** Top N học viên theo XP, dùng cho leaderboard trang chủ. */
    java.util.List<User> findByOrderByTotalXpDesc(Pageable pageable);

    /** Hạng của 1 user = số người có nhiều XP hơn + 1 — không cần tải cả bảng. */
    long countByTotalXpGreaterThan(Long totalXp);
}
