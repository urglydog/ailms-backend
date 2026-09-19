package com.lms.communication.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.communication.dto.AssignmentDto.CreateReq;
import com.lms.communication.dto.AssignmentDto.Res;
import com.lms.communication.dto.AssignmentDto.StudentAssignmentRes;
import com.lms.communication.dto.AssignmentDto.SubmissionRes;
import com.lms.communication.entity.AssignmentSubmission;
import com.lms.communication.entity.CourseAssignment;
import com.lms.communication.repository.AssignmentSubmissionRepository;
import com.lms.communication.repository.CourseAssignmentRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * "Giao tiếp > Bài tập" (19/09/2026) — bài tập tự luận/nộp file gắn với 1 bài học, Giảng viên
 * chấm điểm + phản hồi thủ công. Khác {@code QuizService} (trắc nghiệm tự chấm, không cần
 * Giảng viên can thiệp) — xem docblock {@link com.lms.communication.entity.CourseAssignment}.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final long MAX_SUBMISSION_SIZE_BYTES = 20L * 1024 * 1024;

    private final CourseAssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    @Transactional
    public Res createAssignment(String instructorEmail, Long lessonId, CreateReq req) {
        Lesson lesson = loadOwnedLesson(instructorEmail, lessonId);
        if (req.title() == null || req.title().isBlank()) {
            throw new InvalidRequestException("Tiêu đề bài tập không được để trống");
        }

        CourseAssignment assignment = new CourseAssignment();
        assignment.setLesson(lesson);
        assignment.setTitle(req.title().trim());
        assignment.setInstructions(req.instructions());
        assignment.setDueDate(req.dueDate());
        assignment.setMaxScore(req.maxScore());
        return toRes(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<Res> listForInstructor(String email, Long courseId) {
        List<CourseAssignment> assignments = courseId != null
                ? assignmentRepository.findByLesson_Chapter_Course_IdOrderByCreatedAtDesc(courseId)
                : assignmentRepository.findByLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(email);
        return assignments.stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<Res> listForLesson(String instructorEmail, Long lessonId) {
        loadOwnedLesson(instructorEmail, lessonId);
        return assignmentRepository.findByLesson_IdOrderByIdAsc(lessonId).stream().map(this::toRes).toList();
    }

    @Transactional
    public void deleteAssignment(String instructorEmail, Long id) {
        CourseAssignment assignment = loadOwnedAssignment(instructorEmail, id);
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<SubmissionRes> listSubmissions(String instructorEmail, Long assignmentId) {
        loadOwnedAssignment(instructorEmail, assignmentId);
        return submissionRepository.findByAssignment_IdOrderBySubmittedAtDesc(assignmentId).stream()
                .map(this::toSubmissionRes)
                .toList();
    }

    @Transactional
    public SubmissionRes gradeSubmission(String instructorEmail, Long submissionId, Integer score, String feedback) {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentSubmission", submissionId));
        CourseAssignment assignment = submission.getAssignment();
        if (!assignment.getLesson().getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền chấm bài nộp này");
        }
        submission.setScore(score);
        submission.setFeedback(feedback);
        submission.setGradedAt(LocalDateTime.now());
        AssignmentSubmission saved = submissionRepository.save(submission);

        notificationService.notify(
                submission.getStudent().getId(), "ASSIGNMENT_GRADED",
                "Bài tập \"" + assignment.getTitle() + "\" đã được chấm điểm",
                score != null ? "Điểm: " + score + (assignment.getMaxScore() != null ? "/" + assignment.getMaxScore() : "") : "Giảng viên đã phản hồi bài nộp của bạn",
                "/learn/" + assignment.getLesson().getId());

        return toSubmissionRes(saved);
    }

    @Transactional(readOnly = true)
    public List<StudentAssignmentRes> listForStudent(String studentEmail, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        Long courseId = lesson.getChapter().getCourse().getId();
        if (!enrollmentRepository.existsByUser_EmailAndCourse_Id(studentEmail, courseId)) {
            throw new AccessDeniedDomainException("Bạn cần ghi danh khóa học này để xem bài tập");
        }
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));

        return assignmentRepository.findByLesson_IdOrderByIdAsc(lessonId).stream()
                .map(a -> new StudentAssignmentRes(
                        toRes(a),
                        submissionRepository.findByAssignment_IdAndStudent_Id(a.getId(), student.getId())
                                .map(this::toSubmissionRes)
                                .orElse(null)))
                .toList();
    }

    @Transactional
    public SubmissionRes submit(String studentEmail, Long assignmentId, String textContent, MultipartFile file) {
        CourseAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseAssignment", assignmentId));
        Long courseId = assignment.getLesson().getChapter().getCourse().getId();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new AccessDeniedDomainException("Bạn cần ghi danh khóa học này để nộp bài tập");
        }
        if ((textContent == null || textContent.isBlank()) && (file == null || file.isEmpty())) {
            throw new InvalidRequestException("Cần nhập nội dung hoặc đính kèm file để nộp bài");
        }

        AssignmentSubmission submission = submissionRepository
                .findByAssignment_IdAndStudent_Id(assignmentId, student.getId())
                .orElseGet(AssignmentSubmission::new);
        if (submission.getScore() != null) {
            throw new BusinessRuleViolationException("Bài đã được chấm điểm, không thể nộp lại");
        }
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setTextContent(textContent);
        submission.setSubmittedAt(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            if (file.getSize() > MAX_SUBMISSION_SIZE_BYTES) {
                throw new BusinessRuleViolationException("File nộp bài vượt quá 20MB");
            }
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "bai-nop";
            String sanitized = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
            String key = "assignment-submissions/" + assignmentId + "/" + student.getId() + "/" + UUID.randomUUID() + "-" + sanitized;
            String url;
            try (InputStream in = file.getInputStream()) {
                url = storageService.upload(key, in, file.getSize(), file.getContentType());
            } catch (IOException e) {
                throw new InvalidRequestException("Không tải được file nộp bài: " + e.getMessage());
            }
            submission.setFileUrl(url);
            submission.setFileName(originalName);
        }

        return toSubmissionRes(submissionRepository.save(submission));
    }

    private Lesson loadOwnedLesson(String instructorEmail, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (!lesson.getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên bài học này");
        }
        return lesson;
    }

    private CourseAssignment loadOwnedAssignment(String instructorEmail, Long id) {
        CourseAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseAssignment", id));
        if (!assignment.getLesson().getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên bài tập này");
        }
        return assignment;
    }

    private Res toRes(CourseAssignment a) {
        long submissionCount = submissionRepository.countByAssignment_Id(a.getId());
        long gradedCount = submissionRepository.countByAssignment_IdAndScoreIsNotNull(a.getId());
        return new Res(
                a.getId(), a.getLesson().getId(), a.getLesson().getTitle(),
                a.getLesson().getChapter().getCourse().getId(), a.getLesson().getChapter().getCourse().getTitle(),
                a.getTitle(), a.getInstructions(), a.getDueDate(), a.getMaxScore(), a.getCreatedAt(),
                submissionCount, gradedCount);
    }

    private SubmissionRes toSubmissionRes(AssignmentSubmission s) {
        return new SubmissionRes(
                s.getId(), s.getStudent().getId(), s.getStudent().getFullName(), s.getStudent().getEmail(),
                s.getTextContent(), s.getFileUrl(), s.getFileName(), s.getSubmittedAt(),
                s.getScore(), s.getFeedback(), s.getGradedAt());
    }
}
