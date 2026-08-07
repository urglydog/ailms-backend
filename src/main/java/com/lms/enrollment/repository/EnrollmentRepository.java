package com.lms.enrollment.repository;

import com.lms.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Enrollment}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** Dùng bởi CourseService (F2.1) để quyết định xoá cứng hay xoá mềm (BR-COURSE-03). */
    boolean existsByCourseId(Long courseId);
}
