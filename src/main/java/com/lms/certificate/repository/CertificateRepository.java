package com.lms.certificate.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.lms.enrollment.repository.projection.CourseCertificateRef;
import com.lms.certificate.entity.Certificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link Certificate}. */
@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    boolean existsByStudent_IdAndCourse_Id(Long studentId, Long courseId);

    Optional<Certificate> findByStudent_IdAndCourse_Id(Long studentId, Long courseId);

    Optional<Certificate> findByCertificateCode(String certificateCode);

    boolean existsByCertificateCode(String certificateCode);

    /** "Chứng chỉ của tôi" — mới cấp nhất lên đầu; FE tự sắp xếp lại theo lựa chọn khác (BR không
     * yêu cầu server-side sort, danh sách 1 học viên không đủ lớn để cần phân trang). */
    List<Certificate> findByStudent_IdOrderByIssuedAtDesc(Long studentId);

    /** Trang hồ sơ công khai — chỉ hiển thị chứng chỉ còn hiệu lực (BR-CERT-07), REVOKED bị ẩn. */
    List<Certificate> findByStudent_IdAndStatusOrderByIssuedAtDesc(Long studentId, com.lms.common.enums.CertificateStatus status);
    /** Mã chứng chỉ của học viên cho các khóa cho trước (1 truy vấn). */
    @Query("SELECT new com.lms.enrollment.repository.projection.CourseCertificateRef(c.course.id, c.certificateCode) FROM Certificate c "
            + "WHERE c.student.id = :studentId AND c.course.id IN :courseIds")
    List<CourseCertificateRef> findCertificateRefsByStudentAndCourseIds(@Param("studentId") Long studentId,
                                                                        @Param("courseIds") Collection<Long> courseIds);

}
