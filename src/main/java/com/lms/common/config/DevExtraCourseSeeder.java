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
import com.lms.enrollment.repository.CourseReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/**
 * Thêm khóa học mẫu cho môi trường DEV để trang chủ có đủ dữ liệu cho nhiều hàng khóa học
 * (Mới ra mắt, Miễn phí, Người mới, theo chủ đề). Chạy sau {@link DevCourseDataSeeder}
 * ({@code @Order(2)}), idempotent theo slug nên chạy lại không nhân đôi dữ liệu.
 *
 * <p>Ảnh bìa là ảnh minh họa của picsum.photos (cùng nguồn với seeder gốc), mỗi khóa một seed riêng.
 */
@Configuration
@Profile("dev")
public class DevExtraCourseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevExtraCourseSeeder.class);

    /** Video YouTube công khai đã dùng ở seeder gốc, tái sử dụng để bài học luôn phát được. */
    private static final String[] VIDEOS = {"bMknfKXIFA8", "gtjxAH8uaP0", "LBr4gLQ4FZw", "c9Wg6Cb_YlU", "j68GnGwd2Qk"};

    private record Spec(String category, String title, String description, String level, long price, int rating) {}

    private static final List<Spec> SPECS = List.of(
            new Spec("lap-trinh-web", "HTML & CSS từ con số 0", "Dựng trang web đầu tiên với HTML5, CSS3 và Flexbox, Grid.", "BEGINNER", 0, 5),
            new Spec("lap-trinh-web", "JavaScript hiện đại cho người mới", "ES6+, DOM, bất đồng bộ và những khái niệm cốt lõi của JavaScript.", "BEGINNER", 249000, 5),
            new Spec("lap-trinh-web", "TypeScript thực chiến", "Kiểu dữ liệu, generic và cách dùng TypeScript trong dự án thật.", "INTERMEDIATE", 349000, 4),
            new Spec("lap-trinh-web", "Xây dựng REST API với Spring Boot", "Thiết kế API, xác thực JWT và kiểm thử với Spring Boot 3.", "INTERMEDIATE", 399000, 5),
            new Spec("khoa-hoc-du-lieu", "SQL cho phân tích dữ liệu", "Truy vấn, JOIN, window function và tối ưu truy vấn thực tế.", "BEGINNER", 0, 5),
            new Spec("khoa-hoc-du-lieu", "Trực quan hóa dữ liệu với Power BI", "Từ bảng dữ liệu thô đến dashboard báo cáo chuyên nghiệp.", "BEGINNER", 299000, 4),
            new Spec("khoa-hoc-du-lieu", "Thống kê ứng dụng với Python", "Xác suất, kiểm định giả thuyết và hồi quy qua ví dụ thực tế.", "INTERMEDIATE", 329000, 4),
            new Spec("tri-tue-nhan-tao", "Prompt Engineering cho công việc", "Viết prompt hiệu quả và tự động hóa công việc với mô hình ngôn ngữ.", "BEGINNER", 0, 5),
            new Spec("tri-tue-nhan-tao", "Machine Learning với scikit-learn", "Xây dựng, đánh giá và triển khai mô hình học máy cơ bản.", "INTERMEDIATE", 449000, 4),
            new Spec("tri-tue-nhan-tao", "Xây dựng chatbot RAG", "Kết hợp tìm kiếm vector và LLM để tạo trợ lý trả lời theo tài liệu.", "ADVANCED", 549000, 5),
            new Spec("thiet-ke-ui-ux", "Figma cho người mới bắt đầu", "Giao diện, component, auto layout và prototype trong Figma.", "BEGINNER", 0, 5),
            new Spec("thiet-ke-ui-ux", "Nghiên cứu người dùng và UX Writing", "Phỏng vấn, persona, hành trình người dùng và viết nội dung giao diện.", "INTERMEDIATE", 279000, 4),
            new Spec("ngoai-ngu-chuyen-nganh", "Viết email tiếng Anh công sở", "Mẫu câu, giọng văn và các tình huống email thường gặp trong công việc.", "BEGINNER", 0, 4),
            new Spec("ngoai-ngu-chuyen-nganh", "Tiếng Anh thuyết trình kỹ thuật", "Trình bày ý tưởng, demo sản phẩm và trả lời câu hỏi bằng tiếng Anh.", "INTERMEDIATE", 299000, 5));

    @Bean
    @Order(3)
    public ApplicationRunner seedDevExtraCourses(
            CategoryRepository categoryRepository,
            CourseRepository courseRepository,
            ChapterRepository chapterRepository,
            LessonRepository lessonRepository,
            CourseReviewRepository courseReviewRepository,
            UserRepository userRepository) {
        return args -> {
            User instructor = userRepository.findByEmail("instructor@lms.local").orElse(null);
            User student1 = userRepository.findByEmail("student1@lms.local").orElse(null);
            User student2 = userRepository.findByEmail("student2@lms.local").orElse(null);
            if (instructor == null || student1 == null || student2 == null) {
                log.info("Bo qua seed khoa hoc bo sung: chua co tai khoan mau");
                return;
            }

            int created = 0;
            for (int i = 0; i < SPECS.size(); i++) {
                Spec spec = SPECS.get(i);
                if (courseRepository.existsBySlug(SlugGenerator.slugify(spec.title()))) {
                    continue;
                }
                Category category = categoryRepository.findBySlug(spec.category()).orElse(null);
                if (category == null) {
                    continue;
                }
                Course course = createCourse(courseRepository, chapterRepository, lessonRepository,
                        instructor, category, spec, i);
                review(courseReviewRepository, student1, course, spec.rating(), "Nội dung rõ ràng, dễ áp dụng.");
                review(courseReviewRepository, student2, course, Math.max(3, spec.rating() - (i % 2)),
                        "Giảng viên giải thích dễ hiểu.");
                recalcAvgRating(courseRepository, courseReviewRepository, course);
                created++;
            }
            if (created > 0) {
                log.info("Da seed them {} khoa PUBLISHED (kem review) cho trang chu", created);
            }
        };
    }

    private Course createCourse(
            CourseRepository courseRepository, ChapterRepository chapterRepository, LessonRepository lessonRepository,
            User instructor, Category category, Spec spec, int index) {
        Course course = new Course();
        course.setTitle(spec.title());
        course.setSlug(SlugGenerator.slugify(spec.title()));
        course.setDescription(spec.description());
        course.setThumbnailUrl("https://picsum.photos/seed/lms-extra-" + (index + 1) + "/640/360");
        course.setLevel(spec.level());
        course.setPrice(BigDecimal.valueOf(spec.price()));
        course.setIsFree(spec.price() == 0);
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setReferralCode(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        course.setStatus(CourseStatus.PUBLISHED);
        Course saved = courseRepository.save(course);

        String video = VIDEOS[index % VIDEOS.length];
        Chapter chapter1 = createChapter(chapterRepository, saved, "Chương 1: Nhập môn", 0);
        createLesson(lessonRepository, chapter1, "Giới thiệu tổng quan", 0, true, video);
        createLesson(lessonRepository, chapter1, "Chuẩn bị môi trường", 1, false, video);
        Chapter chapter2 = createChapter(chapterRepository, saved, "Chương 2: Thực hành", 1);
        createLesson(lessonRepository, chapter2, "Bài thực hành 1", 0, false, video);
        createLesson(lessonRepository, chapter2, "Bài thực hành 2", 1, false, video);
        return saved;
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
        lesson.setStatus("READY");
        lesson.setVideoSource("YOUTUBE");
        lesson.setVideoUrl("https://www.youtube.com/watch?v=" + youtubeId);
        lesson.setYoutubeId(youtubeId);
        lesson.setDurationSec(300 + order * 120);
        lessonRepository.save(lesson);
    }

    private void review(CourseReviewRepository courseReviewRepository, User user, Course course, int rating, String comment) {
        CourseReview review = new CourseReview();
        review.setUser(user);
        review.setCourse(course);
        review.setRating(rating);
        review.setComment(comment);
        review.setIsHidden(false);
        courseReviewRepository.save(review);
    }

    /** Khớp công thức của CourseReviewService: trung bình rating chưa bị ẩn. */
    private void recalcAvgRating(
            CourseRepository courseRepository, CourseReviewRepository courseReviewRepository, Course course) {
        List<CourseReview> visible = courseReviewRepository.findByCourse_IdAndIsHiddenFalse(
                course.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        if (visible.isEmpty()) {
            return;
        }
        double average = visible.stream().mapToInt(CourseReview::getRating).average().orElse(0);
        course.setAvgRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        courseRepository.save(course);
    }
}
