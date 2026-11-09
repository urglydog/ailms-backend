package com.lms.certificate.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.CertificateStatus;
import com.lms.enrollment.entity.Enrollment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Chứng chỉ hoàn thành khóa học (doc/DacTa_ChucNangChungChi.md).
 *
 * <p>BR-CERT-02: mỗi cặp (student, course) chỉ có ĐÚNG 1 bản ghi — {@code uk_certificates_student_course}
 * ở tầng DB là chốt chặn cuối cùng chống trùng khi việc cấp bị gọi lại (retry/race), tầng service
 * (xem {@code CertificateService.issueIfEligible}) chỉ kiểm tra trước để tránh query dư thừa.
 *
 * <p>BR-CERT-04: {@code studentNameSnapshot}/{@code courseNameSnapshot}/{@code courseHoursSnapshot}/
 * {@code instructorNameSnapshot} là SNAPSHOT tại thời điểm cấp — KHÔNG tính lại từ
 * {@code student}/{@code course} lúc hiển thị (cùng khuôn với {@code Payment.platformFee}/
 * {@code instructorEarning} chốt cứng lúc PAID). Đổi tên học viên/khóa học/giảng viên sau này,
 * hoặc khóa học bị archive/xóa, không ảnh hưởng nội dung chứng chỉ đã phát hành.
 */
@Entity
@Table(name = "certificates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_certificates_code", columnNames = "certificate_code"),
                @UniqueConstraint(name = "uk_certificates_student_course", columnNames = {"student_id", "course_id"})
        })
@Getter
@Setter
public class Certificate extends BaseEntity {

    /** Mã công khai in trên chứng chỉ + dùng ở URL xác thực (BR-CERT-03), dạng
     * {@code LL-{năm}-{8 ký tự random}} — sinh ở {@code CertificateService}. */
    @Column(name = "certificate_code", nullable = false, length = 20)
    private String certificateCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "student_name_snapshot", nullable = false)
    private String studentNameSnapshot;

    @Column(name = "course_name_snapshot", nullable = false, length = 500)
    private String courseNameSnapshot;

    @Column(name = "course_hours_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal courseHoursSnapshot;

    @Column(name = "instructor_name_snapshot", nullable = false)
    private String instructorNameSnapshot;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CertificateStatus status = CertificateStatus.ACTIVE;

    /** BR-CERT-09: null cho tới lần tải PDF đầu tiên (render on-demand rồi cache lên B2). */
    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    /** Đổi thiết kế chứng chỉ sau này thì tăng {@code CertificatePdfRenderer.TEMPLATE_VERSION}
     * để buộc render lại thay vì trả {@link #pdfUrl} cache cũ. */
    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;
}
