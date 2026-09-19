package com.lms.communication.repository;

import com.lms.communication.entity.Announcement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByCourse_IdOrderByCreatedAtDesc(Long courseId);

    List<Announcement> findByCourse_Instructor_EmailOrderByCreatedAtDesc(String email);
}
