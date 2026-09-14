package com.lms.catalog.repository;

import com.lms.catalog.entity.Lesson;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Lesson}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    long countByChapter_CourseId(Long courseId);

    /** BR-COURSE-01: chỉ bài học đã có video hợp lệ (status=READY, Giai đoạn 4) mới tính. */
    long countByChapter_CourseIdAndStatus(Long courseId, String status);

    List<Lesson> findByChapterIdOrderByDisplayOrderAsc(Long chapterId);

    /** "Học ngay" (my-courses) — bài học đầu tiên của khoá theo đúng thứ tự chương rồi bài. */
    Optional<Lesson> findFirstByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(Long courseId);

    /** UC09 mở rộng (14/09/2026) — tổng thời lượng video của khóa, dùng cho bộ lọc "Video Duration". */
    @Query("SELECT COALESCE(SUM(l.durationSec), 0) FROM Lesson l WHERE l.chapter.course.id = :courseId")
    int sumDurationSecByCourseId(@Param("courseId") Long courseId);

    /**
     * UC10 mở rộng (14/09/2026) — "ngôn ngữ gốc" hiển thị ở trang chi tiết khóa kiểu Udemy.
     * {@code sourceLanguage} tự điền sau lần bóc băng đầu tiên của MỖI bài (BR-DUB-09), nên lấy
     * bài đầu tiên (theo đúng thứ tự chương-bài) đã có giá trị này làm đại diện cho cả khóa.
     */
    Optional<Lesson> findFirstByChapter_CourseIdAndSourceLanguageIsNotNullOrderByChapter_DisplayOrderAscDisplayOrderAsc(Long courseId);
}
