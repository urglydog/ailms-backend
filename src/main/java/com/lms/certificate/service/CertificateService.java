package com.lms.certificate.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.LessonRepository;
import com.lms.certificate.dto.CertificateDto.PublicRes;
import com.lms.certificate.dto.CertificateDto.Res;
import com.lms.certificate.dto.CertificateDto.VerifyRes;
import com.lms.certificate.entity.Certificate;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.certificate.util.CertificatePdfRenderer;
import com.lms.certificate.util.CertificatePdfRenderer.CertificateData;
import com.lms.common.enums.CertificateStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.entity.Enrollment;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp + tra cứu chứng chỉ hoàn thành khóa học (doc/DacTa_ChucNangChungChi.md, BR-CERT-01..10).
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    /** Bỏ các ký tự dễ nhầm lẫn khi đọc bằng mắt: 0/O, 1/I/L (BR-CERT-03). */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_RANDOM_LENGTH = 8;
    private static final int CODE_GENERATION_MAX_ATTEMPTS = 5;

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${lms.certificate.verify-base-url}")
    private String verifyBaseUrl;

    /**
     * BR-CERT-01/02 — gọi ngay sau khi {@code Enrollment.completedAt} vừa được set lần đầu (xem
     * {@code LessonProgressService.recalculateEnrollmentProgress}). Idempotent: kiểm tồn tại
     * trước, và bắt {@link DataIntegrityViolationException} từ ràng buộc UNIQUE DB làm lưới chặn
     * cuối cùng nếu 2 request đua nhau cùng đạt mốc 100% (hiếm nhưng có thể xảy ra).
     */
    @Transactional
    public void issueIfEligible(Enrollment enrollment) {
        Course course = enrollment.getCourse();
        User student = enrollment.getUser();

        if (certificateRepository.existsByStudent_IdAndCourse_Id(student.getId(), course.getId())) {
            return;
        }

        Certificate certificate = new Certificate();
        certificate.setCertificateCode(generateUniqueCode());
        certificate.setStudent(student);
        certificate.setCourse(course);
        certificate.setEnrollment(enrollment);
        certificate.setStudentNameSnapshot(student.getFullName());
        certificate.setCourseNameSnapshot(course.getTitle());
        certificate.setCourseHoursSnapshot(computeCourseHours(course.getId()));
        certificate.setInstructorNameSnapshot(course.getInstructor().getFullName());
        certificate.setCompletedAt(enrollment.getCompletedAt());
        certificate.setIssuedAt(LocalDateTime.now());
        certificate.setStatus(CertificateStatus.ACTIVE);
        certificate.setTemplateVersion(CertificatePdfRenderer.TEMPLATE_VERSION);

        try {
            certificateRepository.saveAndFlush(certificate);
        } catch (DataIntegrityViolationException e) {
            log.info("Chung chi (student={}, course={}) da duoc cap boi request khac, bo qua.",
                    student.getId(), course.getId());
            return;
        }

        notificationService.notify(student.getId(), "COURSE_COMPLETED",
                "🎓 Chúc mừng bạn đã hoàn thành khóa học!",
                "Bạn đã hoàn thành khóa học \"" + course.getTitle() + "\" và nhận được chứng chỉ.",
                "/certificates/" + certificate.getCertificateCode());
    }

    /** BR-CERT-05 — dùng lại ĐÚNG công thức "X giờ học" đã hiển thị ở trang chi tiết khóa học
     * ({@code LessonRepository.sumDurationSecByCourseId}), không tính lại theo công thức khác. */
    private BigDecimal computeCourseHours(Long courseId) {
        int totalSeconds = lessonRepository.sumDurationSecByCourseId(courseId);
        return BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private String generateUniqueCode() {
        int year = LocalDateTime.now().getYear();
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_ATTEMPTS; attempt++) {
            String candidate = "LL-" + year + "-" + randomSuffix();
            if (!certificateRepository.existsByCertificateCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Khong sinh duoc ma chung chi duy nhat sau " + CODE_GENERATION_MAX_ATTEMPTS + " lan thu");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(CODE_RANDOM_LENGTH);
        for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<Res> getMyCertificates(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return certificateRepository.findByStudent_IdOrderByIssuedAtDesc(user.getId()).stream()
                .map(this::toRes)
                .toList();
    }

    @Transactional(readOnly = true)
    public Res getCertificateDetail(String email, String certificateCode) {
        return toRes(findOwned(email, certificateCode));
    }

    /**
     * BR-CERT-09 — cache PDF trên B2 sau lần render đầu tiên; render lại nếu chưa có cache hoặc
     * thiết kế đã đổi ({@code templateVersion} lệch với bản hiện hành).
     */
    @Transactional
    public byte[] getPdf(String email, String certificateCode) {
        Certificate certificate = findOwned(email, certificateCode);

        boolean cacheValid = certificate.getPdfUrl() != null
                && certificate.getTemplateVersion() != null
                && certificate.getTemplateVersion() == CertificatePdfRenderer.TEMPLATE_VERSION;
        if (cacheValid) {
            try {
                return fetchCachedPdf(certificate.getPdfUrl());
            } catch (Exception e) {
                log.warn("Khong tai duoc PDF cache tren B2 cho chung chi {}, render lai.", certificateCode, e);
            }
        }

        byte[] pdf = CertificatePdfRenderer.render(toRenderData(certificate));
        String key = "certificates/" + certificate.getId() + "/" + UUID.randomUUID() + ".pdf";
        String url = storageService.upload(key, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        certificate.setPdfUrl(url);
        certificate.setTemplateVersion(CertificatePdfRenderer.TEMPLATE_VERSION);
        certificateRepository.save(certificate);
        return pdf;
    }

    private byte[] fetchCachedPdf(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("B2 tra ve HTTP " + response.statusCode() + " cho " + url);
        }
        return response.body();
    }

    @Transactional(readOnly = true)
    public VerifyRes verify(String certificateCode) {
        return certificateRepository.findByCertificateCode(certificateCode)
                .map(c -> new VerifyRes(
                        true,
                        c.getCertificateCode(),
                        c.getStudentNameSnapshot(),
                        c.getCourseNameSnapshot(),
                        c.getCourseHoursSnapshot(),
                        c.getInstructorNameSnapshot(),
                        c.getCompletedAt(),
                        c.getStatus(),
                        buildVerifyUrl(c.getCertificateCode())
                ))
                .orElseGet(VerifyRes::notFound);
    }

    private Certificate findOwned(String email, String certificateCode) {
        Certificate certificate = certificateRepository.findByCertificateCode(certificateCode)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", certificateCode));
        if (!certificate.getStudent().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedDomainException("Bạn không sở hữu chứng chỉ này.");
        }
        return certificate;
    }

    private Res toRes(Certificate c) {
        return new Res(
                c.getCertificateCode(),
                c.getCourse().getId(),
                c.getCourseNameSnapshot(),
                c.getCourse().getSlug(),
                c.getStudentNameSnapshot(),
                c.getCourseHoursSnapshot(),
                c.getInstructorNameSnapshot(),
                c.getCompletedAt(),
                c.getIssuedAt(),
                c.getStatus(),
                buildVerifyUrl(c.getCertificateCode())
        );
    }

    public PublicRes toPublicRes(Certificate c) {
        return new PublicRes(
                c.getCertificateCode(),
                c.getCourseNameSnapshot(),
                c.getCourse().getCategory().getName(),
                c.getStudentNameSnapshot(),
                c.getCourseHoursSnapshot(),
                c.getInstructorNameSnapshot(),
                c.getCompletedAt(),
                c.getIssuedAt(),
                c.getStatus(),
                buildVerifyUrl(c.getCertificateCode())
        );
    }

    private CertificateData toRenderData(Certificate c) {
        return new CertificateData(
                c.getStudentNameSnapshot(),
                c.getCourseNameSnapshot(),
                c.getCourseHoursSnapshot(),
                CertificatePdfRenderer.formatDate(c.getCompletedAt()),
                c.getInstructorNameSnapshot(),
                c.getCertificateCode(),
                buildVerifyUrl(c.getCertificateCode())
        );
    }

    private String buildVerifyUrl(String certificateCode) {
        return verifyBaseUrl + "/" + certificateCode;
    }
}
