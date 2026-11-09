package com.lms.certificate.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Category;
import com.lms.catalog.repository.LessonRepository;
import com.lms.certificate.dto.CertificateDto.VerifyRes;
import com.lms.certificate.entity.Certificate;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.common.enums.CertificateStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.entity.Enrollment;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-CERT-01/02/03 — cấp tự động, idempotent, mã công khai không đoán được. */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private CertificateRepository certificateRepository;
    @Mock private UserRepository userRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private NotificationService notificationService;
    @Mock private StorageService storageService;

    private CertificateService service;

    private User student;
    private Course course;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new CertificateService(certificateRepository, userRepository, lessonRepository,
                notificationService, storageService);
        ReflectionTestUtils.setField(service, "verifyBaseUrl", "http://localhost:3000/verify");

        User instructor = new User();
        instructor.setId(2L);
        instructor.setFullName("ThS. Trần Thị B");

        Category category = new Category();
        category.setId(1L);
        category.setName("Lập trình");

        course = new Course();
        course.setId(10L);
        course.setTitle("Lập trình Python cho người mới bắt đầu");
        course.setInstructor(instructor);
        course.setCategory(category);

        student = new User();
        student.setId(1L);
        student.setFullName("Nguyễn Văn A");
        student.setEmail("student1@lms.local");

        enrollment = new Enrollment();
        enrollment.setId(100L);
        enrollment.setUser(student);
        enrollment.setCourse(course);
        enrollment.setCompletedAt(LocalDateTime.of(2026, 9, 26, 10, 0));
    }

    @Test
    void issueIfEligible_firstTime_createsCertificateWithSnapshotAndNotifies() {
        when(certificateRepository.existsByStudent_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(certificateRepository.existsByCertificateCode(any())).thenReturn(false);
        when(lessonRepository.sumDurationSecByCourseId(10L)).thenReturn(115200); // 32h

        service.issueIfEligible(enrollment);

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).saveAndFlush(captor.capture());
        Certificate saved = captor.getValue();

        assertThat(saved.getStudent()).isEqualTo(student);
        assertThat(saved.getCourse()).isEqualTo(course);
        assertThat(saved.getEnrollment()).isEqualTo(enrollment);
        assertThat(saved.getStudentNameSnapshot()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getCourseNameSnapshot()).isEqualTo("Lập trình Python cho người mới bắt đầu");
        assertThat(saved.getInstructorNameSnapshot()).isEqualTo("ThS. Trần Thị B");
        assertThat(saved.getCourseHoursSnapshot()).isEqualByComparingTo("32.00");
        assertThat(saved.getCompletedAt()).isEqualTo(enrollment.getCompletedAt());
        assertThat(saved.getStatus()).isEqualTo(CertificateStatus.ACTIVE);

        // BR-CERT-03: LL-{năm}-{8 ký tự} — không đoán được, không tuần tự.
        assertThat(saved.getCertificateCode()).matches(Pattern.compile("^LL-\\d{4}-[A-Z0-9]{8}$"));

        verify(notificationService).notify(eq(1L), eq("COURSE_COMPLETED"), any(), any(), any());
    }

    @Test
    void issueIfEligible_alreadyIssued_doesNothing() {
        when(certificateRepository.existsByStudent_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        service.issueIfEligible(enrollment);

        verify(certificateRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }

    /** Race hiếm gặp: 2 request cùng đạt mốc 100% gần như đồng thời — request thứ 2 phải coi như
     * đã cấp thành công (không ném lỗi lên caller), dựa vào ràng buộc UNIQUE DB. */
    @Test
    void issueIfEligible_raceConditionViolatesUniqueConstraint_swallowsSilently() {
        when(certificateRepository.existsByStudent_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(certificateRepository.existsByCertificateCode(any())).thenReturn(false);
        when(lessonRepository.sumDurationSecByCourseId(10L)).thenReturn(0);
        when(certificateRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_certificates_student_course"));

        service.issueIfEligible(enrollment);

        verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any());
    }

    @Test
    void verify_activeCertificate_returnsFoundTrue() {
        Certificate cert = new Certificate();
        cert.setStudentNameSnapshot("Nguyễn Văn A");
        cert.setCourseNameSnapshot("Python");
        cert.setCompletedAt(enrollment.getCompletedAt());
        cert.setStatus(CertificateStatus.ACTIVE);
        when(certificateRepository.findByCertificateCode("LL-2026-ABCDEFGH")).thenReturn(Optional.of(cert));

        VerifyRes res = service.verify("LL-2026-ABCDEFGH");

        assertThat(res.found()).isTrue();
        assertThat(res.status()).isEqualTo(CertificateStatus.ACTIVE);
    }

    @Test
    void verify_unknownCode_returnsFoundFalse() {
        when(certificateRepository.findByCertificateCode("LL-0000-XXXXXXXX")).thenReturn(Optional.empty());

        VerifyRes res = service.verify("LL-0000-XXXXXXXX");

        assertThat(res.found()).isFalse();
    }

    @Test
    void verify_revokedCertificate_stillFoundButStatusRevoked() {
        Certificate cert = new Certificate();
        cert.setStudentNameSnapshot("Nguyễn Văn A");
        cert.setCourseNameSnapshot("Python");
        cert.setCompletedAt(enrollment.getCompletedAt());
        cert.setStatus(CertificateStatus.REVOKED);
        when(certificateRepository.findByCertificateCode("LL-2026-REVOKED1")).thenReturn(Optional.of(cert));

        VerifyRes res = service.verify("LL-2026-REVOKED1");

        assertThat(res.found()).isTrue();
        assertThat(res.status()).isEqualTo(CertificateStatus.REVOKED);
    }

    @Test
    void getCertificateDetail_notOwner_throwsAccessDenied() {
        Certificate cert = new Certificate();
        cert.setStudent(student); // owner = student1@lms.local
        cert.setCourse(course);
        cert.setCertificateCode("LL-2026-ABCDEFGH");
        when(certificateRepository.findByCertificateCode("LL-2026-ABCDEFGH")).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> service.getCertificateDetail("khac@lms.local", "LL-2026-ABCDEFGH"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void getCertificateDetail_unknownCode_throwsNotFound() {
        when(certificateRepository.findByCertificateCode("LL-0000-XXXXXXXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCertificateDetail("student1@lms.local", "LL-0000-XXXXXXXX"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
