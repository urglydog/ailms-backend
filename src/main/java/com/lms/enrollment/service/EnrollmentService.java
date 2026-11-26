package com.lms.enrollment.service;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.lms.enrollment.repository.projection.CourseProgressRef;
import com.lms.enrollment.repository.projection.CourseLessonRef;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.service.CourseAccessService;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.enrollment.service.ResumePointResolver.LatestProgress;
import com.lms.enrollment.service.ResumePointResolver.ResumePoint;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.CartItemRepository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Khóa học của tôi" — danh sách khóa Student đã sở hữu (UC12/UC14 tạo ra Enrollment; ở đây chỉ
 * đọc). Chưa có luồng ghi danh/mua khóa thật (Giai đoạn 3) nên module này hiện chỉ có phần đọc,
 * phục vụ trực tiếp F2.2 (Student cần biết mình đã sở hữu khóa nào để vào đánh giá — UC23).
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CartItemRepository cartItemRepository;
    private final CourseAccessService courseAccessService;
    private final CertificateRepository certificateRepository;

    /**
     * "Khóa học của tôi". Hiệu năng (08/11/2026, đợt 1 — PLAN_HieuNang_HeThong.md): trước đây mỗi khóa đã ghi
     * danh chạy ~10 truy vấn riêng (đánh giá ×2, bài đầu, lần truy cập cuối, chứng chỉ, TOÀN BỘ bài học để tính
     * "học tiếp", tiến độ gần nhất, giảng viên, danh mục) → 45 khóa = 476 câu SQL. Giờ: 1 truy vấn nạp
     * enrollment + course/category/instructor, rồi mỗi loại dữ liệu phụ lấy MỘT lần cho cả danh sách
     * ({@code WHERE course_id IN (...)}) và ghép bằng Map — số truy vấn không tăng theo số khóa.
     */
    @Transactional(readOnly = true)
    public List<Res> getMyEnrollments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        List<Enrollment> enrollments = enrollmentRepository.findWithCourseByUser_Email(email);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = enrollments.stream().map(e -> e.getCourse().getId()).toList();
        Long userId = user.getId();

        Map<Long, Integer> ratingByCourse = new HashMap<>();
        courseReviewRepository.findReviewRefsByUserAndCourseIds(userId, courseIds)
                .forEach(r -> ratingByCourse.put(r.courseId(), r.rating()));
        Map<Long, String> certificateByCourse = new HashMap<>();
        certificateRepository.findCertificateRefsByStudentAndCourseIds(userId, courseIds)
                .forEach(c -> certificateByCourse.put(c.courseId(), c.certificateCode()));
        // Truy vấn đã sắp đúng thứ tự khóa → chương → bài, nên giữ thứ tự khi gom theo khóa.
        Map<Long, List<Long>> lessonsByCourse = new HashMap<>();
        Map<Long, CourseLessonRef> lessonRefById = new HashMap<>();
        for (CourseLessonRef l : lessonRepository.findLessonRefsByCourseIds(courseIds)) {
            lessonsByCourse.computeIfAbsent(l.courseId(), k -> new ArrayList<>()).add(l.lessonId());
            lessonRefById.put(l.lessonId(), l);
        }
        // Bản ghi tiến độ mới nhất của từng khóa: dùng cho cả "học tiếp" và "truy cập gần nhất".
        Map<Long, CourseProgressRef> latestByCourse = new HashMap<>();
        for (CourseProgressRef p : lessonProgressRepository.findProgressRefsByUserAndCourseIds(userId, courseIds)) {
            latestByCourse.merge(p.courseId(), p, (x, y) -> isAfter(y.updatedAt(), x.updatedAt()) ? y : x);
        }

        return enrollments.stream().map(enrollment -> {
            Course course = enrollment.getCourse();
            Long courseId = course.getId();
            List<Long> orderedLessonIds = lessonsByCourse.getOrDefault(courseId, List.of());
            CourseProgressRef latest = latestByCourse.get(courseId);
            ResumePoint resume = ResumePointResolver.resolve(orderedLessonIds, latest == null ? null
                    : new LatestProgress(latest.lessonId(),
                            latest.lastPositionSec() == null ? 0 : latest.lastPositionSec(),
                            Boolean.TRUE.equals(latest.completed())));
            Integer myRating = ratingByCourse.get(courseId);
            CourseLessonRef resumeRef = resume.lessonId() == null ? null : lessonRefById.get(resume.lessonId());
            return new Res(
                    courseId,
                    course.getTitle(),
                    course.getSlug(),
                    course.getThumbnailUrl(),
                    course.getCategory().getName(),
                    course.getIsFree(),
                    course.getPrice(),
                    ratingByCourse.containsKey(courseId),
                    enrollment.getProgressPct(),
                    enrollment.getCompletedAt(),
                    // BR-PROGRESS-04: Quiz thật làm ở Giai đoạn 7 — để null an toàn ở đây.
                    null,
                    // "Học ngay" vào thẳng bài đầu tiên theo thứ tự chương/bài (BR-COURSE-01: khóa đã publish có ≥1 bài).
                    orderedLessonIds.isEmpty() ? null : orderedLessonIds.get(0),
                    course.getInstructor().getFullName(),
                    myRating,
                    enrollment.getEnrolledAt(),
                    latest == null ? null : latest.updatedAt(),
                    certificateByCourse.get(courseId),
                    resume.lessonId(),
                    resume.positionSec(),
                    resumeRef == null ? null : resumeRef.lessonTitle(),
                    resumeRef == null ? null : resumeRef.durationSec()
            );
        }).toList();
    }

    private static boolean isAfter(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return false;
        }
        return b == null || a.isAfter(b);
    }

    @Transactional
    public void enrollFreeCourse(String email, Long courseId, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        // "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — PRIVATE_INVITE/PRIVATE_PASSWORD
        // chặn NGAY TẠI ĐÂY, trước cả các điều kiện BR-ENROLL-01 bên dưới.
        courseAccessService.verifyCanEnroll(course, email, password);

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Chỉ có thể ghi danh khóa học đã xuất bản.");
        }
        if (!Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Khóa học không miễn phí. Yêu cầu thanh toán.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setProgressPct(BigDecimal.ZERO);
        enrollmentRepository.save(enrollment);
        // Gio hang (06/09/2026, mo rong) — da so huu roi thi khong con ly do nam trong gio nua.
        cartItemRepository.deleteByUser_IdAndCourse_Id(user.getId(), course.getId());
    }

    /**
     * Dùng cho luồng thanh toán thành công (F3.2 gọi sang).
     * @param payment Giao dịch đã được xác nhận PAID.
     */
    @Transactional
    public void createFromPayment(Payment payment) {
        // Mua lại sau khi từng bị hoàn tiền (11/11/2026): KÍCH HOẠT LẠI dòng đã thu hồi (UNIQUE user+course
        // không cho chèn dòng mới; entity có @SQLRestriction nên existsBy... bên dưới không thấy dòng này).
        // Giữ nguyên progress/completedAt → không cộng lại XP hoàn thành khóa.
        if (enrollmentRepository.reactivate(payment.getUser().getId(), payment.getCourse().getId(), LocalDateTime.now()) > 0) {
            // Chứng chỉ đã bị thu hồi lúc hoàn tiền (nếu có) có hiệu lực trở lại vì khóa vẫn ở trạng thái hoàn thành.
            certificateRepository.findByStudent_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId())
                    .filter(c -> c.getStatus() == com.lms.common.enums.CertificateStatus.REVOKED)
                    .ifPresent(c -> c.setStatus(com.lms.common.enums.CertificateStatus.ACTIVE));
            cartItemRepository.deleteByUser_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId());
            return;
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId())) {
            return;
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(payment.getUser());
        enrollment.setCourse(payment.getCourse());
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setProgressPct(BigDecimal.ZERO);
        enrollmentRepository.save(enrollment);
        // Gio hang (06/09/2026, mo rong) — thanh toan xong (tu gio hang hoac mua truc tiep)
        // thi xoa khoi gio hang neu co, tranh con nam lai o do sau khi da so huu.
        cartItemRepository.deleteByUser_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId());
    }

    /**
     * Thu hồi MỀM quyền học khi giao dịch bị hoàn tiền (11/11/2026, Giai đoạn B). Idempotent: học viên đã
     * không còn ghi danh hiệu lực thì trả {@code false}. Chứng chỉ của khóa (nếu có) chuyển REVOKED.
     */
    @Transactional
    public boolean revokeForRefund(Payment payment, String reason) {
        Long userId = payment.getUser().getId();
        Long courseId = payment.getCourse().getId();
        int revoked = enrollmentRepository.revoke(userId, courseId, payment.getId(), reason, LocalDateTime.now());
        certificateRepository.findByStudent_IdAndCourse_Id(userId, courseId)
                .filter(c -> c.getStatus() == com.lms.common.enums.CertificateStatus.ACTIVE)
                .ifPresent(c -> c.setStatus(com.lms.common.enums.CertificateStatus.REVOKED));
        return revoked > 0;
    }
}
