package com.lms.payment.repository;

import com.lms.payment.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link CartItem} (giỏ hàng, 06/09/2026 — mở rộng ngoài đặc tả gốc). */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"course", "course.instructor"})
    List<CartItem> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<CartItem> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    void deleteByUser_IdAndCourse_Id(Long userId, Long courseId);
}
