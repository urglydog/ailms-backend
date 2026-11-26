package com.lms.chat.repository;

import com.lms.chat.entity.LessonAiSuggestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonAiSuggestionRepository extends JpaRepository<LessonAiSuggestion, Long> {

    Optional<LessonAiSuggestion> findByLessonId(Long lessonId);

    List<LessonAiSuggestion> findByStatusOrderByUpdatedAtAsc(String status, Pageable pageable);
}
