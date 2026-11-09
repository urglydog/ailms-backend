package com.lms.communication.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.communication.dto.AnnouncementDto.CreateReq;
import com.lms.communication.dto.AnnouncementDto.Res;
import com.lms.communication.entity.Announcement;
import com.lms.communication.repository.AnnouncementRepository;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Giao tiếp > Thông báo" của Giảng viên (19/09/2026) — gửi broadcast tới toàn bộ học viên đã
 * ghi danh 1 khóa học. Tận dụng {@link NotificationService} có sẵn (BR-NOTIFY-01: lưu DB + bắn
 * WebSocket) để fan-out, không tự dựng lại cơ chế gửi.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;

    @Transactional
    public Res create(String instructorEmail, CreateReq req) {
        Course course = loadOwnedCourse(instructorEmail, req.courseId());
        if (req.title() == null || req.title().isBlank()) {
            throw new InvalidRequestException("Tiêu đề thông báo không được để trống");
        }
        if (req.content() == null || req.content().isBlank()) {
            throw new InvalidRequestException("Nội dung thông báo không được để trống");
        }

        Announcement announcement = new Announcement();
        announcement.setCourse(course);
        announcement.setTitle(req.title().trim());
        announcement.setContent(req.content().trim());
        Announcement saved = announcementRepository.save(announcement);

        String linkUrl = "/courses/" + course.getSlug();
        for (Enrollment enrollment : enrollmentRepository.findByCourseId(course.getId())) {
            notificationService.notify(
                    enrollment.getUser().getId(), "ANNOUNCEMENT",
                    "[" + course.getTitle() + "] " + saved.getTitle(), saved.getContent(), linkUrl);
        }

        return toRes(saved);
    }

    @Transactional(readOnly = true)
    public List<Res> listForInstructor(String email, Long courseId) {
        List<Announcement> announcements = courseId != null
                ? announcementRepository.findByCourse_IdOrderByCreatedAtDesc(courseId)
                : announcementRepository.findByCourse_Instructor_EmailOrderByCreatedAtDesc(email);
        return announcements.stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<Res> listForStudent(String email, Long courseId) {
        if (!enrollmentRepository.existsByUser_EmailAndCourse_Id(email, courseId)) {
            throw new AccessDeniedDomainException("Bạn cần ghi danh khóa học này để xem thông báo");
        }
        return announcementRepository.findByCourse_IdOrderByCreatedAtDesc(courseId).stream()
                .map(this::toRes)
                .toList();
    }

    @Transactional
    public void delete(String instructorEmail, Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
        if (!announcement.getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền xóa thông báo này");
        }
        announcementRepository.delete(announcement);
    }

    private Course loadOwnedCourse(String instructorEmail, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên khóa học này");
        }
        return course;
    }

    private Res toRes(Announcement a) {
        return new Res(
                a.getId(), a.getCourse().getId(), a.getCourse().getTitle(),
                a.getTitle(), a.getContent(), a.getCreatedAt());
    }
}
