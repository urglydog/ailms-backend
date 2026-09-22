package com.lms.catalog.service;

import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.CourseActivityLog;
import com.lms.catalog.repository.CourseActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ghi và đọc nhật ký hoạt động của giảng viên trên 1 khóa học (panel "Hoạt động gần đây").
 * Best-effort: lỗi khi ghi log KHÔNG được làm hỏng thao tác chính của người dùng.
 */
@Service
@RequiredArgsConstructor
public class CourseActivityLogService {

    private final CourseActivityLogRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public void log(Course course, String actorEmail, String description) {
        try {
            CourseActivityLog entry = new CourseActivityLog();
            entry.setCourse(course);
            userRepository.findByEmail(actorEmail).ifPresent(entry::setActor);
            entry.setDescription(description);
            repository.save(entry);
        } catch (Exception ignored) {
            // Best-effort — không để lỗi ghi log ảnh hưởng thao tác chính.
        }
    }

    @Transactional(readOnly = true)
    public List<CourseActivityLog> getRecent(Long courseId, int limit) {
        return repository.findByCourse_IdOrderByCreatedAtDesc(courseId, PageRequest.of(0, limit));
    }
}
