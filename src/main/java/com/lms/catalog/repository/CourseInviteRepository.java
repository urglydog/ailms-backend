package com.lms.catalog.repository;

import com.lms.catalog.entity.CourseInvite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseInviteRepository extends JpaRepository<CourseInvite, Long> {

    boolean existsByCourse_IdAndEmail(Long courseId, String email);

    List<CourseInvite> findByCourse_IdOrderByCreatedAtDesc(Long courseId);

    void deleteByCourse_IdAndEmail(Long courseId, String email);
}
