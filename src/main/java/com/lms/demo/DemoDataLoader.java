package com.lms.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lms.certificate.entity.Certificate;
import com.lms.certificate.repository.CertificateRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RevenueSource;
import com.lms.common.enums.Role;
import com.lms.demo.DemoDataPlanner.CourseInput;
import com.lms.demo.DemoDataPlanner.EnrollmentSpec;
import com.lms.demo.DemoDataPlanner.Plan;
import com.lms.enrollment.entity.CourseReview;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nạp dữ liệu demo thật (khóa học từ playlist YouTube + học viên, ghi danh, thanh toán, đánh giá,
 * chứng chỉ giả lập) để các dashboard có số liệu. Chỉ chạy khi {@code app.demo-seed.enabled=true}
 * (biến môi trường {@code APP_DEMO_SEED_ENABLED}) — dùng được cả ở local lẫn server.
 *
 * <p>An toàn khi chạy lại: chỉ THÊM, không xóa/ghi đè. Khóa học đã tồn tại (theo slug) bị bỏ qua cùng
 * toàn bộ hoạt động của nó; tài khoản đã tồn tại (theo email) được dùng lại. Mật khẩu chung lấy từ
 * {@code app.demo-seed.password}, để trống thì không nạp.
 */
@Configuration
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final long SEED = 20261101L;
    private static final int STUDENTS = 80;

    private final List<Object[]> backdates = new ArrayList<>();

    @Bean
    @Order(10)
    public ApplicationRunner loadDemoData(
            TransactionTemplate tx, ObjectMapper mapper, EntityManager em, JdbcTemplate jdbc,
            PasswordEncoder encoder, UserRepository users, CategoryRepository categories,
            CourseRepository courses, ChapterRepository chapters, LessonRepository lessons,
            EnrollmentRepository enrollments, LessonProgressRepository progresses,
            CourseReviewRepository reviews, PaymentRepository payments, CertificateRepository certificates,
            @Value("${app.demo-seed.password:}") String password) {
        return args -> {
            if (password.isBlank()) {
                log.warn("Bo qua nap du lieu demo: chua dat app.demo-seed.password");
                return;
            }
            DemoCatalog catalog;
            try (InputStream in = getClass().getResourceAsStream("/demo/courses.json")) {
                catalog = mapper.readValue(in, DemoCatalog.class);
            }
            tx.executeWithoutResult(status -> {
                backdates.clear();
                String hash = encoder.encode(password);
                LocalDateTime now = LocalDateTime.now();
                Random rnd = new Random(SEED + 1);

                catalog.categories().forEach((slug, name) -> {
                    if (!categories.existsBySlug(slug)) {
                        Category c = new Category();
                        c.setName(name);
                        c.setSlug(slug);
                        categories.save(c);
                    }
                });

                Map<String, User> instructors = new HashMap<>();
                for (DemoCatalog.Instructor i : catalog.instructors()) {
                    User u = users.findByEmail(i.email()).orElseGet(() -> {
                        User n = new User();
                        n.setEmail(i.email());
                        n.setFullName(i.fullName());
                        n.setHeadline(i.headline());
                        n.setBio(i.bio());
                        n.setAvatarUrl(i.avatarUrl());
                        n.setRole(Role.INSTRUCTOR);
                        n.setPasswordHash(hash);
                        User saved = users.save(n);
                        backdates.add(new Object[] {"users", saved.getId(), now.minusDays(400)});
                        return saved;
                    });
                    // Tài khoản giảng viên demo do bộ nạp sở hữu: đồng bộ hồ sơ (ảnh, tiêu đề, giới thiệu) theo danh mục mới nhất.
                    if (i.avatarUrl() != null) {
                        u.setAvatarUrl(i.avatarUrl());
                    }
                    u.setHeadline(i.headline());
                    u.setBio(i.bio());
                    users.save(u);
                    instructors.put(i.email(), u);
                }

                List<DemoCatalog.Course> defs = catalog.courses();
                Map<Integer, Course> created = new HashMap<>();
                Map<Integer, List<Lesson>> lessonsOf = new HashMap<>();
                List<CourseInput> inputs = new ArrayList<>();
                for (int idx = 0; idx < defs.size(); idx++) {
                    DemoCatalog.Course d = defs.get(idx);
                    int lessonCount = d.chapters().stream().mapToInt(c -> c.lessons().size()).sum();
                    long totalSec = d.chapters().stream().flatMap(c -> c.lessons().stream())
                            .mapToLong(DemoCatalog.Lesson::durationSec).sum();
                    boolean published = "PUBLISHED".equals(d.status());
                    int ageDays = d.ageDays() > 0 ? d.ageDays() : d.ageMonths() * 30;
                    inputs.add(new CourseInput(lessonCount, d.price(), ageDays, published, totalSec));
                    if (courses.existsBySlug(d.slug())) {
                        refreshExisting(jdbc, d);
                        continue;
                    }
                    Course course = new Course();
                    course.setTitle(d.title());
                    course.setSlug(d.slug());
                    course.setDescription(d.description());
                    course.setThumbnailUrl(d.thumbnailUrl());
                    course.setLevel(d.level());
                    course.setPrice(BigDecimal.valueOf(d.price()));
                    course.setIsFree(d.price() == 0);
                    course.setCategory(categories.findBySlug(d.category()).orElseThrow());
                    course.setInstructor(instructors.get(d.instructor().email()));
                    course.setReferralCode(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
                    course.setStatus(CourseStatus.valueOf(d.status()));
                    course.setTotalLessons(lessonCount);
                    if (course.getStatus() == CourseStatus.PENDING) {
                        course.setResubmitCount(1);
                    }
                    Course saved = courses.save(course);
                    backdates.add(new Object[] {"courses", saved.getId(), now.minusDays(ageDays)});
                    List<Lesson> all = new ArrayList<>();
                    int chapterOrder = 0;
                    for (DemoCatalog.Chapter ch : d.chapters()) {
                        Chapter chapter = new Chapter();
                        chapter.setTitle(ch.title());
                        chapter.setDisplayOrder(chapterOrder++);
                        chapter.setCourse(saved);
                        chapters.save(chapter);
                        int lessonOrder = 0;
                        for (DemoCatalog.Lesson l : ch.lessons()) {
                            Lesson lesson = new Lesson();
                            lesson.setTitle(l.title());
                            lesson.setDescription(l.description());
                            lesson.setChapter(chapter);
                            lesson.setDisplayOrder(lessonOrder++);
                            lesson.setIsPreview(l.isPreview());
                            lesson.setStatus("READY");
                            lesson.setVideoSource("YOUTUBE");
                            lesson.setVideoUrl("https://www.youtube.com/watch?v=" + l.youtubeId());
                            lesson.setYoutubeId(l.youtubeId());
                            lesson.setDurationSec(l.durationSec());
                            all.add(lessons.save(lesson));
                        }
                    }
                    created.put(idx, saved);
                    lessonsOf.put(idx, all);
                }

                Plan plan = DemoDataPlanner.plan(SEED, STUDENTS, inputs);
                List<User> students = new ArrayList<>();
                for (DemoDataPlanner.StudentSpec s : plan.students()) {
                    User u = users.findByEmail(s.email()).orElseGet(() -> {
                        User n = new User();
                        n.setEmail(s.email());
                        n.setFullName(s.fullName());
                        n.setAvatarUrl(s.avatarUrl());
                        n.setRole(Role.STUDENT);
                        n.setPasswordHash(hash);
                        User saved = users.save(n);
                        backdates.add(new Object[] {"users", saved.getId(), now.minusDays(s.joinedDaysAgo())});
                        return saved;
                    });
                    if (u.getAvatarUrl() == null && s.avatarUrl() != null) {
                        u.setAvatarUrl(s.avatarUrl());
                        users.save(u);
                    }
                    students.add(u);
                }

                Map<Long, Long> xp = new HashMap<>();
                Map<Long, java.util.Set<LocalDate>> days = new HashMap<>();
                int enrolledCount = 0;
                int paidCount = 0;
                for (EnrollmentSpec e : plan.enrollments()) {
                    Course course = created.get(e.course());
                    if (course == null) {
                        continue;
                    }
                    User student = students.get(e.student());
                    if (enrollments.existsByUser_IdAndCourse_Id(student.getId(), course.getId())) {
                        continue;
                    }
                    List<Lesson> ls = lessonsOf.get(e.course());
                    LocalDateTime enrolledAt = now.minusDays(e.enrolledDaysAgo()).minusMinutes(rnd.nextInt(600));

                    if (e.paid()) {
                        Payment p = new Payment();
                        p.setTxnRef("DEMO-" + course.getSlug() + "-" + student.getId());
                        p.setUser(student);
                        p.setCourse(course);
                        p.setAmount(course.getPrice());
                        p.setOriginalAmount(course.getPrice());
                        RevenueSource source = e.referral() ? RevenueSource.INSTRUCTOR_REFERRAL : RevenueSource.ORGANIC;
                        BigDecimal platformShare = e.referral() ? new BigDecimal("0.03") : new BigDecimal("0.63");
                        BigDecimal fee = course.getPrice().multiply(platformShare).setScale(2, RoundingMode.HALF_UP);
                        p.setRevenueSource(source);
                        p.setPlatformFee(fee);
                        p.setInstructorEarning(course.getPrice().subtract(fee));
                        p.setPaymentMethod(e.method());
                        p.setStatus(PaymentStatus.PAID);
                        p.setGatewayTxnNo("DEMO" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
                        p.setPaidAt(enrolledAt);
                        p.setBillingName(student.getFullName());
                        Payment savedPayment = payments.save(p);
                        backdates.add(new Object[] {"payments", savedPayment.getId(), enrolledAt});
                        paidCount++;
                    }

                    Enrollment en = new Enrollment();
                    en.setUser(student);
                    en.setCourse(course);
                    en.setEnrolledAt(enrolledAt);
                    int total = ls.size();
                    en.setProgressPct(BigDecimal.valueOf(e.completedLessons() * 100.0 / total)
                            .setScale(2, RoundingMode.HALF_UP));
                    LocalDateTime lastActivity = enrolledAt;
                    java.util.Set<LocalDate> userDays = days.computeIfAbsent(student.getId(), k -> new java.util.HashSet<>());
                    userDays.add(enrolledAt.toLocalDate());
                    for (int i = 0; i < e.completedLessons() + (e.partialLesson() ? 1 : 0) && i < total; i++) {
                        boolean partial = i >= e.completedLessons();
                        Lesson lesson = ls.get(i);
                        LessonProgress lp = new LessonProgress();
                        lp.setUser(student);
                        lp.setLesson(lesson);
                        int watched = partial ? lesson.getDurationSec() * (20 + rnd.nextInt(50)) / 100 : lesson.getDurationSec();
                        lp.setWatchedSec(watched);
                        lp.setLastPositionSec(watched);
                        lp.setIsCompleted(!partial);
                        progresses.save(lp);
                        lastActivity = lastActivity.plusHours(3 + rnd.nextInt(30));
                        if (lastActivity.isAfter(now)) {
                            lastActivity = now.minusMinutes(rnd.nextInt(300));
                        }
                        userDays.add(lastActivity.toLocalDate());
                        backdates.add(new Object[] {"lesson_progress", lp.getId(), lastActivity});
                    }
                    boolean finished = e.completedLessons() == total;
                    if (finished) {
                        en.setCompletedAt(lastActivity);
                    }
                    Enrollment savedEnrollment = enrollments.save(en);
                    backdates.add(new Object[] {"enrollments", savedEnrollment.getId(), enrolledAt});
                    enrolledCount++;
                    xp.merge(student.getId(), e.completedLessons() * 10L + (finished ? 200L : 0L), Long::sum);

                    if (finished) {
                        Certificate cert = new Certificate();
                        cert.setCertificateCode("LL-" + lastActivity.getYear() + "-"
                                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
                        cert.setStudent(student);
                        cert.setCourse(course);
                        cert.setEnrollment(savedEnrollment);
                        cert.setStudentNameSnapshot(student.getFullName());
                        cert.setCourseNameSnapshot(course.getTitle());
                        long sec = ls.stream().mapToLong(Lesson::getDurationSec).sum();
                        cert.setCourseHoursSnapshot(BigDecimal.valueOf(sec / 3600.0).setScale(2, RoundingMode.HALF_UP));
                        cert.setInstructorNameSnapshot(course.getInstructor().getFullName());
                        cert.setCompletedAt(lastActivity);
                        cert.setIssuedAt(lastActivity);
                        Certificate savedCert = certificates.save(cert);
                        backdates.add(new Object[] {"certificates", savedCert.getId(), lastActivity});
                    }
                    if (e.rating() != null) {
                        CourseReview r = new CourseReview();
                        r.setUser(student);
                        r.setCourse(course);
                        r.setRating(e.rating());
                        r.setComment(e.comment());
                        r.setIsHidden(false);
                        CourseReview savedReview = reviews.save(r);
                        backdates.add(new Object[] {"course_reviews", savedReview.getId(), lastActivity.plusHours(2)});
                    }
                }

                em.flush();
                for (Object[] b : backdates) {
                    jdbc.update("UPDATE " + b[0] + " SET created_at = ? WHERE id = ?", Timestamp.valueOf((LocalDateTime) b[2]), b[1]);
                }

                for (Course course : created.values()) {
                    Double avg = jdbc.queryForObject(
                            "SELECT AVG(rating) FROM course_reviews WHERE course_id = ? AND is_hidden = FALSE", Double.class, course.getId());
                    if (avg != null) {
                        jdbc.update("UPDATE courses SET avg_rating = ? WHERE id = ?",
                                BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP), course.getId());
                    }
                }
                xp.forEach((uid, v) -> jdbc.update("UPDATE users SET total_xp = total_xp + ? WHERE id = ?", v, uid));
                days.forEach((uid, set) -> {
                    set.forEach(d -> jdbc.update(
                            "INSERT IGNORE INTO user_learning_days (user_id, activity_date, created_at) VALUES (?, ?, NOW())", uid, d));
                    LocalDate last = set.stream().max(LocalDate::compareTo).orElse(null);
                    jdbc.update("INSERT IGNORE INTO user_streaks (user_id, current_streak, longest_streak, last_activity_date, timezone, freeze_used_count, version) VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 0, 0)",
                            uid, 1 + (int) (uid % 4), 3 + (int) (uid % 9), last);
                });
                log.info("Nap du lieu demo: {} khoa moi, {} ghi danh ({} co thanh toan).", created.size(), enrolledCount, paidCount);
            });
        };
    }

    /**
     * Khóa demo đã tồn tại: nội dung hiển thị (tên, mô tả, ảnh bìa, tên chương và mô tả bài) do bộ nạp sở hữu nên được
     * đồng bộ theo file mới nhất. Không đụng tới giá, trạng thái, ghi danh hay bất kỳ dữ liệu hoạt động nào.
     */
    private void refreshExisting(JdbcTemplate jdbc, DemoCatalog.Course d) {
        jdbc.update("UPDATE courses SET title = ?, description = ?, thumbnail_url = ? WHERE slug = ?",
                d.title(), d.description(), d.thumbnailUrl(), d.slug());
        Long courseId = jdbc.queryForObject("SELECT id FROM courses WHERE slug = ?", Long.class, d.slug());
        int order = 0;
        for (DemoCatalog.Chapter ch : d.chapters()) {
            jdbc.update("UPDATE chapters SET title = ? WHERE course_id = ? AND display_order = ?", ch.title(), courseId, order++);
            for (DemoCatalog.Lesson l : ch.lessons()) {
                jdbc.update("UPDATE lessons l JOIN chapters c ON c.id = l.chapter_id SET l.title = ?, l.description = ? "
                        + "WHERE c.course_id = ? AND l.youtube_id = ?", l.title(), l.description(), courseId, l.youtubeId());
            }
        }
    }
}
