package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.service.CourseAccessService;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
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

    /**
     * UpComming_Plan.md A1 — sinh PDF chứng chỉ hoàn thành on-the-fly (không lưu file lên B2,
     * không có entity Certificate riêng). Chỉ cho tải khi enrollment đã có completedAt.
     */
    @Transactional(readOnly = true)
    public byte[] generateCertificatePdf(String email, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUser_EmailAndCourse_Id(email, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", courseId));

        if (enrollment.getCompletedAt() == null) {
            throw new BusinessRuleViolationException(
                    "CERTIFICATE_NOT_READY", "Bạn chưa hoàn thành 100% khóa học này nên chưa có chứng chỉ.");
        }

        try {
            var document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate(), 50, 50, 60, 60);
            var out = new java.io.ByteArrayOutputStream();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();

            var borderFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 12);
            var titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 32, new java.awt.Color(37, 99, 235));
            var nameFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 24);
            var bodyFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 14);
            var smallFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 10, java.awt.Color.GRAY);

            var title = new com.lowagie.text.Paragraph("CHỨNG CHỈ HOÀN THÀNH", titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            title.setSpacingAfter(30);
            document.add(title);

            var introLine = new com.lowagie.text.Paragraph("Chứng nhận", bodyFont);
            introLine.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            document.add(introLine);

            var nameLine = new com.lowagie.text.Paragraph(enrollment.getUser().getFullName(), nameFont);
            nameLine.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            nameLine.setSpacingBefore(10);
            nameLine.setSpacingAfter(10);
            document.add(nameLine);

            var courseLine = new com.lowagie.text.Paragraph(
                    "đã hoàn thành khóa học \"" + enrollment.getCourse().getTitle() + "\"", bodyFont);
            courseLine.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            courseLine.setSpacingAfter(30);
            document.add(courseLine);

            var dateLine = new com.lowagie.text.Paragraph(
                    "Ngày hoàn thành: " + enrollment.getCompletedAt().toLocalDate(), borderFont);
            dateLine.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            document.add(dateLine);

            var codeLine = new com.lowagie.text.Paragraph(
                    "Mã xác thực: " + enrollment.getCertificateCode(), smallFont);
            codeLine.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            codeLine.setSpacingBefore(40);
            document.add(codeLine);

            document.close();
            return out.toByteArray();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("Không sinh được PDF chứng chỉ", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Res> getMyEnrollments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return enrollmentRepository.findByUser_Email(email).stream()
                .map(enrollment -> mapToRes(user, enrollment))
                .toList();
    }

    private Res mapToRes(User user, Enrollment enrollment) {
        Course course = enrollment.getCourse();
        boolean alreadyReviewed = courseReviewRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId());
        Integer myRating = courseReviewRepository.findByUser_IdAndCourse_Id(user.getId(), course.getId())
                .map(review -> review.getRating())
                .orElse(null);
        // "Học ngay" phải vào thẳng bài học, không phải trang chi tiết khoá — bấm vào bài đầu
        // tiên theo đúng thứ tự chương/bài (BR-COURSE-01 đảm bảo khoá đã publish có ≥1 bài).
        Long firstLessonId = lessonRepository
                .findFirstByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(course.getId())
                .map(lesson -> lesson.getId())
                .orElse(null);
        LocalDateTime lastAccessedAt = lessonProgressRepository
                .findLastAccessedAtByUserIdAndCourseId(user.getId(), course.getId());
        return new Res(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getThumbnailUrl(),
                course.getCategory().getName(),
                course.getIsFree(),
                course.getPrice(),
                alreadyReviewed,
                enrollment.getProgressPct(),
                enrollment.getCompletedAt(),
                // BR-PROGRESS-04: Quiz thật làm ở Giai đoạn 7 — để null an toàn ở đây.
                null,
                firstLessonId,
                course.getInstructor().getFullName(),
                myRating,
                enrollment.getEnrolledAt(),
                lastAccessedAt
        );
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
}
