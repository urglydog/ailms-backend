package com.lms.common.config;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.util.SlugGenerator;
import com.lms.common.enums.CourseStatus;
import com.lms.enrollment.entity.CourseReview;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/**
 * Dữ liệu mẫu cho F2.2 (khám phá công khai &amp; đánh giá) — môi trường DEV.
 *
 * <p><b>Vì sao là Java, không phải file SQL trong {@code db/dev/}</b>: Flyway (bao gồm
 * {@code V100__seed_dev_reference.sql}) chạy trong vòng đời khởi tạo bean, TRƯỚC khi
 * {@link DevDataSeeder} (ApplicationRunner) seed User — vì mật khẩu Bcrypt phải băm bằng
 * {@code PasswordEncoder} thật lúc khởi động, không nhúng hash sẵn vào SQL được. Course cần
 * {@code instructor_id}, Enrollment/CourseReview cần {@code user_id} (NOT NULL) nên bắt buộc
 * chạy SAU khi User đã tồn tại — do đó phải là {@code ApplicationRunner} thứ 2, đánh dấu
 * {@code @Order(2)} để chạy sau {@code DevDataSeeder}'s {@code @Order(1)}.
 *
 * <p>Chỉ hoạt động ở profile {@code dev}, idempotent (đã có Course thì bỏ qua).
 *
 * <p><b>File này dùng làm dữ liệu mẫu để cả nhóm test — không sửa logic nghiệp vụ ở đây.</b>
 */
@Configuration
@Profile("dev")
public class DevCourseDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevCourseDataSeeder.class);

    @Bean
    @Order(2)
    public ApplicationRunner seedDevCourseData(
            CategoryRepository categoryRepository,
            CourseRepository courseRepository,
            ChapterRepository chapterRepository,
            LessonRepository lessonRepository,
            EnrollmentRepository enrollmentRepository,
            CourseReviewRepository courseReviewRepository,
            UserRepository userRepository) {
        return args -> {
            if (courseRepository.count() > 0) {
                log.info("Bo qua seed khoa hoc: da co {} khoa hoc trong CSDL", courseRepository.count());
                return;
            }

            User instructor = userRepository.findByEmail("instructor@lms.local")
                    .orElseThrow(() -> new IllegalStateException(
                            "Chua co instructor@lms.local — DevDataSeeder phai chay truoc (kiem tra @Order)"));
            User student1 = userRepository.findByEmail("student1@lms.local")
                    .orElseThrow(() -> new IllegalStateException("Chua co student1@lms.local"));
            User student2 = userRepository.findByEmail("student2@lms.local")
                    .orElseThrow(() -> new IllegalStateException("Chua co student2@lms.local"));

            Category webCategory = category(categoryRepository, "lap-trinh-web");
            Category dataCategory = category(categoryRepository, "khoa-hoc-du-lieu");
            Category aiCategory = category(categoryRepository, "tri-tue-nhan-tao");
            Category uiuxCategory = category(categoryRepository, "thiet-ke-ui-ux");
            Category langCategory = category(categoryRepository, "ngoai-ngu-chuyen-nganh");

            // ── 5 khóa PUBLISHED — đủ chương/bài để test duyệt công khai + đánh giá ──
            // Mỗi khóa gán 1 video YouTube công khai thật, đúng chủ đề (đã xác nhận tồn tại qua
            // oEmbed) — durationSec vẫn dùng công thức giả lập ngắn cho gọn danh sách, không phải
            // thời lượng thật của video gốc (seeder ghi thẳng vào DB, không qua luồng kiểm
            // BR-CHUNK-01 của LessonService như khi Giảng viên tự nạp).
            Course course1 = createPublishedCourse(courseRepository, chapterRepository, lessonRepository,
                    instructor, webCategory, "Lập trình Web với React & Next.js",
                    "Xây dựng ứng dụng web hiện đại từ con số 0 với React 19 và Next.js 15 App Router.",
                    "BEGINNER", new BigDecimal("299000"), 1, "bMknfKXIFA8");
            Course course2 = createPublishedCourse(courseRepository, chapterRepository, lessonRepository,
                    instructor, dataCategory, "Phân tích dữ liệu với Python & Pandas",
                    "Làm chủ Pandas, NumPy để xử lý và trực quan hóa dữ liệu thực tế.",
                    "INTERMEDIATE", BigDecimal.ZERO, 2, "gtjxAH8uaP0");
            Course course3 = createPublishedCourse(courseRepository, chapterRepository, lessonRepository,
                    instructor, aiCategory, "Nhập môn Trí tuệ nhân tạo",
                    "Tổng quan Machine Learning, Deep Learning và ứng dụng thực tế của AI.",
                    "BEGINNER", new BigDecimal("499000"), 3, "LBr4gLQ4FZw");
            Course course4 = createPublishedCourse(courseRepository, chapterRepository, lessonRepository,
                    instructor, uiuxCategory, "Thiết kế giao diện UI/UX cơ bản",
                    "Nguyên lý thiết kế, Figma và quy trình UX từ nghiên cứu người dùng đến prototype.",
                    "BEGINNER", BigDecimal.ZERO, 4, "c9Wg6Cb_YlU");
            Course course5 = createPublishedCourse(courseRepository, chapterRepository, lessonRepository,
                    instructor, langCategory, "Tiếng Anh giao tiếp cho IT",
                    "Từ vựng và phản xạ giao tiếp tiếng Anh chuyên ngành công nghệ thông tin.",
                    "INTERMEDIATE", new BigDecimal("199000"), 5, "j68GnGwd2Qk");

            // ── 2 khóa chưa duyệt — để tự kiểm chứng KHÔNG hiện ở danh sách công khai ──
            createUnpublishedCourse(courseRepository, instructor, webCategory,
                    "Khóa học đang soạn thảo", CourseStatus.DRAFT, 6);
            createUnpublishedCourse(courseRepository, instructor, dataCategory,
                    "Khóa học chờ duyệt", CourseStatus.PENDING, 7);

            // ── Enrollment — điều kiện để được đánh giá (BR-ENROLL-01) ──
            enroll(enrollmentRepository, student1, course1);
            enroll(enrollmentRepository, student1, course2);
            enroll(enrollmentRepository, student1, course3);
            enroll(enrollmentRepository, student2, course1);
            enroll(enrollmentRepository, student2, course4);
            // course5: CỐ TÌNH không seed review — để cả 2 tài khoản test luôn được luồng
            // "viết đánh giá thành công" (không phải mọi khóa-đã-sở-hữu đều đã có sẵn review).
            enroll(enrollmentRepository, student1, course5);
            enroll(enrollmentRepository, student2, course5);

            // ── CourseReview — 1 review cố tình đặt isHidden=true để test Admin ngay ──
            review(courseReviewRepository, student1, course1, 5,
                    "Khóa học rất hay, giảng viên giải thích dễ hiểu!", false);
            review(courseReviewRepository, student2, course1, 4,
                    "Nội dung tốt nhưng nhịp độ hơi nhanh.", false);
            review(courseReviewRepository, student1, course2, 5,
                    "Miễn phí mà chất lượng, cảm ơn giảng viên.", false);
            review(courseReviewRepository, student1, course3, 3,
                    "Nội dung khá cơ bản, mong có thêm ví dụ thực tế.", true);
            review(courseReviewRepository, student2, course4, 5,
                    "Thiết kế đẹp, học xong áp dụng ngay được vào công việc.", false);

            recalcAvgRating(courseRepository, courseReviewRepository, course1);
            recalcAvgRating(courseRepository, courseReviewRepository, course2);
            recalcAvgRating(courseRepository, courseReviewRepository, course3);
            recalcAvgRating(courseRepository, courseReviewRepository, course4);
            recalcAvgRating(courseRepository, courseReviewRepository, course5);

            log.info("Da seed 5 khoa PUBLISHED + 2 khoa chua duyet, 7 enrollment, 5 review (1 da an) "
                    + "— course5 (Tieng Anh giao tiep cho IT) co enrollment nhung chua co review, "
                    + "dung de test luong viet danh gia thanh cong");
        };
    }

    private Category category(CategoryRepository categoryRepository, String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException(
                        "Chua co danh muc slug=" + slug + " — V100__seed_dev_reference.sql phai chay truoc"));
    }

    private Course createPublishedCourse(
            CourseRepository courseRepository, ChapterRepository chapterRepository, LessonRepository lessonRepository,
            User instructor, Category category, String title, String description,
            String level, BigDecimal price, int coverSeed, String youtubeId) {
        Course course = new Course();
        course.setTitle(title);
        course.setSlug(SlugGenerator.slugify(title));
        course.setDescription(description);
        course.setThumbnailUrl("https://picsum.photos/seed/lms-course-" + coverSeed + "/640/360");
        course.setLevel(level);
        course.setPrice(price);
        course.setIsFree(price.compareTo(BigDecimal.ZERO) == 0);
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setStatus(CourseStatus.PUBLISHED);
        Course saved = courseRepository.save(course);

        Chapter chapter1 = createChapter(chapterRepository, saved, "Chương 1: Nhập môn", 0);
        createLesson(lessonRepository, chapter1, "Giới thiệu tổng quan", 0, true, youtubeId);
        createLesson(lessonRepository, chapter1, "Cài đặt môi trường", 1, false, youtubeId);

        Chapter chapter2 = createChapter(chapterRepository, saved, "Chương 2: Thực hành", 1);
        createLesson(lessonRepository, chapter2, "Bài tập thực hành 1", 0, false, youtubeId);
        createLesson(lessonRepository, chapter2, "Bài tập thực hành 2", 1, false, youtubeId);

        return saved;
    }

    private void createUnpublishedCourse(
            CourseRepository courseRepository, User instructor, Category category,
            String title, CourseStatus status, int coverSeed) {
        Course course = new Course();
        course.setTitle(title);
        course.setSlug(SlugGenerator.slugify(title));
        course.setDescription("Nội dung đang được hoàn thiện.");
        course.setThumbnailUrl("https://picsum.photos/seed/lms-course-" + coverSeed + "/640/360");
        course.setLevel("BEGINNER");
        course.setPrice(BigDecimal.ZERO);
        course.setIsFree(true);
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setStatus(status);
        courseRepository.save(course);
    }

    private Chapter createChapter(ChapterRepository chapterRepository, Course course, String title, int order) {
        Chapter chapter = new Chapter();
        chapter.setTitle(title);
        chapter.setDisplayOrder(order);
        chapter.setCourse(course);
        return chapterRepository.save(chapter);
    }

    private void createLesson(
            LessonRepository lessonRepository, Chapter chapter, String title, int order, boolean isPreview,
            String youtubeId) {
        Lesson lesson = new Lesson();
        lesson.setTitle(title);
        lesson.setChapter(chapter);
        lesson.setDisplayOrder(order);
        lesson.setIsPreview(isPreview);
        // Video YouTube công khai thật (Giai đoạn 4, UC34) — status=READY vì đã có video hợp lệ.
        lesson.setStatus("READY");
        lesson.setVideoSource("YOUTUBE");
        lesson.setVideoUrl("https://www.youtube.com/watch?v=" + youtubeId);
        lesson.setYoutubeId(youtubeId);
        lesson.setDurationSec(300 + order * 120);
        lessonRepository.save(lesson);
    }

    private void enroll(EnrollmentRepository enrollmentRepository, User user, Course course) {
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    private void review(
            CourseReviewRepository courseReviewRepository, User user, Course course,
            int rating, String comment, boolean hidden) {
        CourseReview review = new CourseReview();
        review.setUser(user);
        review.setCourse(course);
        review.setRating(rating);
        review.setComment(comment);
        review.setIsHidden(hidden);
        courseReviewRepository.save(review);
    }

    /** Khớp đúng công thức của CourseReviewService: trung bình rating chưa bị ẩn. */
    private void recalcAvgRating(
            CourseRepository courseRepository, CourseReviewRepository courseReviewRepository, Course course) {
        List<CourseReview> visibleReviews = courseReviewRepository.findByCourse_IdAndIsHiddenFalse(
                course.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        if (visibleReviews.isEmpty()) {
            return;
        }
        double average = visibleReviews.stream().mapToInt(CourseReview::getRating).average().orElse(0);
        course.setAvgRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        courseRepository.save(course);
    }
}
