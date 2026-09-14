package com.lms.wishlist.repository;

import com.lms.wishlist.entity.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link WishlistItem} (danh sách yêu thích, 14/09/2026 — mở rộng ngoài đặc tả gốc). */
@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = {"course", "course.instructor", "course.category"})
    List<WishlistItem> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    void deleteByUser_IdAndCourse_Id(Long userId, Long courseId);
}
