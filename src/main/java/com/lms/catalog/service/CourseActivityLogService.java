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

    /**
     * Trả về danh sách đã map sẵn thành Map (không phải entity) — tránh
     * LazyInitializationException khi controller đọc {@code actor.getFullName()}
     * sau khi transaction/session của phương thức này đã đóng.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getRecent(Long courseId, int limit) {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (CourseActivityLog entry : repository.findByCourse_IdOrderByCreatedAtDesc(courseId, PageRequest.of(0, limit))) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", entry.getId());
            map.put("actorName", entry.getActor() != null ? entry.getActor().getFullName() : null);
            map.put("description", entry.getDescription());
            map.put("createdAt", entry.getCreatedAt().toString());
            result.add(map);
        }
        return result;
    }
}
