package com.lms.community.repository;

import com.lms.community.entity.LessonChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonChatRepository extends JpaRepository<LessonChat, String> {
    List<LessonChat> findByLessonIdOrderByCreatedAtAsc(Long lessonId);
}
