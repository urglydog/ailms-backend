package com.lms.material.controller;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.entity.QuizAttemptViolation;
import com.lms.material.repository.ProctoringRecordingRepository;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizAttemptViolationRepository;
import com.lms.material.repository.QuizRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * UC-ANTICHEAT (25/09/2026, rút gọn IA 26/09/2026) — màn hình "Giám sát thi" cho giảng viên.
 *
 * <p><b>Bug UX thật đã sửa</b>: thiết kế ban đầu tách 2 bước (chọn khoá → chọn quiz → mới thấy
 * lượt thi) qua 2 endpoint riêng (`courses/{id}/quizzes` rồi `quizzes/{id}/attempts`) — quá nhiều
 * bước để xem dữ liệu 1 bài thi cụ thể, theo đúng phản hồi thật của giảng viên dùng thử. Gộp còn
 * ĐÚNG 1 endpoint `courses/{courseId}/attempts` trả về TẤT CẢ lượt thi (đã gộp cả tên quiz vào
 * từng dòng) của mọi quiz có giám sát trong 1 khoá — FE giờ chỉ cần course context (đã có sẵn từ
 * URL trang sửa khoá học) + 1 lần gọi API, không cần màn hình trung gian "chọn quiz" nữa.
 *
 * <p>KHÔNG mở rộng {@code InstructorGradebookController} — Gradebook chỉ có điểm số tổng hợp
 * (chỉ lượt gần nhất/cao nhất mỗi học viên), không hợp để nhét thêm video/timeline. Logic đặt
 * thẳng trong controller, đúng phong cách {@code InstructorGradebookController} (inner DTO
 * {@code @Data @Builder}) thay vì tách service riêng.
 */
@RestController
@RequestMapping("/api/v1/instructor/proctoring")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class InstructorProctoringController {

    private final CourseRepository courseRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptViolationRepository quizAttemptViolationRepository;
    private final ProctoringRecordingRepository proctoringRecordingRepository;

    /** Danh sách TẤT CẢ lượt thi (đã nộp) của mọi quiz có bật giám sát trong 1 khoá học, gộp sẵn
     * tên quiz vào từng dòng — không cần màn hình trung gian "chọn quiz" nữa. Sắp xếp mới nhất
     * trước. */
    @GetMapping("/courses/{courseId}/attempts")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AttemptSummaryDto>> getAttempts(Principal principal, @PathVariable Long courseId) {
        Course course = loadOwnedCourse(courseId, principal.getName());
        List<Quiz> quizzes = quizRepository.findByMaterialGeneration_Course_IdAndIsProctoredTrue(course.getId());

        List<AttemptSummaryDto> result = quizzes.stream()
                .flatMap(q -> quizAttemptRepository.findByQuiz_IdAndStatusOrderBySubmittedAtDesc(q.getId(), "COMPLETED").stream()
                        .map(a -> AttemptSummaryDto.builder()
                                .attemptId(a.getId())
                                // MaterialGeneration.title thường NULL (instructor chưa đặt tên
                                // riêng) — cùng quy ước fallback "không tên" đã dùng ở Workspace
                                // học liệu (CourseMaterialsManager.tsx), tránh hiện ID vô nghĩa
                                // kiểu "Bài thi #3" (bug UX thật đã bị phản ánh, 26/09/2026).
                                .quizTitle(q.getMaterialGeneration().getTitle() != null
                                        ? q.getMaterialGeneration().getTitle() : "Đề thi không tên")
                                .studentName(a.getUser().getFullName())
                                .studentEmail(a.getUser().getEmail())
                                .submittedAt(a.getSubmittedAt())
                                .violationCount(a.getViolationCount())
                                .aiRiskLevel(a.getAiRiskLevel())
                                .hasRecording(proctoringRecordingRepository.findByAttempt_Id(a.getId()).isPresent())
                                .build()))
                .sorted((a, b) -> {
                    if (a.getSubmittedAt() == null || b.getSubmittedAt() == null) return 0;
                    return b.getSubmittedAt().compareTo(a.getSubmittedAt());
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/attempts/{attemptId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AttemptDetailDto> getAttemptDetail(Principal principal, @PathVariable Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", attemptId));
        loadOwnedCourse(attempt.getQuiz().getMaterialGeneration().getCourse().getId(), principal.getName());

        var recording = proctoringRecordingRepository.findByAttempt_Id(attemptId).orElse(null);
        List<QuizAttemptViolation> violations = quizAttemptViolationRepository.findByAttempt_IdOrderByCreatedAtAsc(attemptId);
        LocalDateTime startedAt = attempt.getCreatedAt();

        List<ViolationMarkerDto> markers = violations.stream().map(v -> ViolationMarkerDto.builder()
                        .type(v.getType())
                        .detail(v.getDetail())
                        .offsetSec(startedAt != null ? Duration.between(startedAt, v.getCreatedAt()).getSeconds() : 0)
                        .build())
                .toList();

        return ResponseEntity.ok(AttemptDetailDto.builder()
                .attemptId(attempt.getId())
                .studentName(attempt.getUser().getFullName())
                .studentEmail(attempt.getUser().getEmail())
                .submittedAt(attempt.getSubmittedAt())
                .violationCount(attempt.getViolationCount())
                .aiRiskLevel(attempt.getAiRiskLevel())
                .aiRiskExplanation(attempt.getAiRiskExplanation())
                .videoUrl(recording != null ? recording.getVideoUrl() : null)
                .durationSec(recording != null ? recording.getDurationSec() : null)
                .violations(markers)
                .build());
    }

    private Course loadOwnedCourse(Long courseId, String instructorEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Chỉ giảng viên sở hữu khóa học mới xem được màn hình giám sát thi");
        }
        return course;
    }

    @Data
    @Builder
    public static class AttemptSummaryDto {
        private Long attemptId;
        private String quizTitle;
        private String studentName;
        private String studentEmail;
        private LocalDateTime submittedAt;
        private Integer violationCount;
        private String aiRiskLevel;
        private boolean hasRecording;
    }

    @Data
    @Builder
    public static class ViolationMarkerDto {
        private String type;
        private String detail;
        private long offsetSec;
    }

    @Data
    @Builder
    public static class AttemptDetailDto {
        private Long attemptId;
        private String studentName;
        private String studentEmail;
        private LocalDateTime submittedAt;
        private Integer violationCount;
        private String aiRiskLevel;
        private String aiRiskExplanation;
        private String videoUrl;
        private Integer durationSec;
        private List<ViolationMarkerDto> violations;
    }
}
