package com.lms.demo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sinh kế hoạch dữ liệu hoạt động giả lập (học viên, ghi danh, tiến độ, đánh giá) một cách
 * tất định theo {@code seed}, để cùng một seed luôn cho ra cùng dữ liệu. Lớp thuần, không đụng
 * tới CSDL — phần ghi nằm ở {@link DemoDataLoader}.
 */
public final class DemoDataPlanner {

    /** Thông tin tối thiểu của một khóa học dùng để lập kế hoạch. */
    public record CourseInput(int lessonCount, long price, int ageDays, boolean published, long totalSec) {}

    public record StudentSpec(String fullName, String email, int joinedDaysAgo, String avatarUrl) {}

    /** {@code completedLessons}: số bài đã hoàn thành (các bài đầu theo thứ tự); bài kế tiếp xem dở nếu {@code partialLesson}. */
    public record EnrollmentSpec(
            int student, int course, int enrolledDaysAgo, int completedLessons, boolean partialLesson,
            boolean paid, boolean referral, String method, Integer rating, String comment) {}

    public record Plan(List<StudentSpec> students, List<EnrollmentSpec> enrollments) {}

    private static final String[] FAMILY = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"};
    private static final String[] MIDDLE_M = {"Văn", "Minh", "Quốc", "Hữu", "Đức", "Thanh", "Anh", "Gia"};
    private static final String[] MIDDLE_F = {"Thị", "Ngọc", "Thu", "Mai", "Khánh", "Bảo", "Phương", "Hải"};
    private static final String[] GIVEN_M = {"An", "Bình", "Cường", "Dũng", "Hải", "Hiếu", "Huy", "Khoa", "Long", "Nam", "Phúc", "Quân", "Sơn", "Tài", "Thắng", "Trung", "Tuấn", "Việt", "Khang", "Đạt"};
    private static final String[] GIVEN_F = {"Anh", "Chi", "Dung", "Giang", "Hà", "Hạnh", "Hương", "Lan", "Linh", "Mai", "Ngân", "Nhung", "Oanh", "Quỳnh", "Thảo", "Trang", "Uyên", "Vy", "Yến", "Trinh"};
    private static final String[] DRAWN_STYLES = {"adventurer", "lorelei", "fun-emoji", "bottts", "thumbs", "big-smile", "notionists", "avataaars"};
    private static final String[] METHODS = {"VNPAY", "MOMO", "ZALOPAY"};

    private static final String[] GOOD = {
        "Nội dung rõ ràng, dễ theo dõi, mình áp dụng được ngay vào bài tập.",
        "Giảng viên giải thích rất dễ hiểu, có ví dụ thực tế.",
        "Khóa học có lộ trình hợp lý, phù hợp với người mới bắt đầu.",
        "Tính năng lồng tiếng giúp mình học nhanh hơn rất nhiều.",
        "Bài giảng ngắn gọn, đi thẳng vào trọng tâm. Rất đáng học.",
        "Mình đã hoàn thành khóa và tự làm được dự án nhỏ."};
    private static final String[] OK = {
        "Nội dung ổn, một vài phần hơi nhanh nhưng xem lại thì hiểu.",
        "Khóa học tốt, mong có thêm bài tập thực hành đi kèm.",
        "Phù hợp để làm quen, phần nâng cao cần tham khảo thêm."};
    private static final String[] LOW = {
        "Một số bài hơi dài, mình mong chia nhỏ hơn.",
        "Chưa phù hợp lắm với trình độ của mình."};

    private DemoDataPlanner() {}

    public static Plan plan(long seed, int studentCount, List<CourseInput> courses) {
        Random rnd = new Random(seed);
        List<StudentSpec> students = students(rnd, new Random(seed ^ 0x5EEDL), studentCount);
        List<EnrollmentSpec> enrollments = new ArrayList<>();

        for (int c = 0; c < courses.size(); c++) {
            CourseInput course = courses.get(c);
            if (!course.published() || course.lessonCount() == 0) {
                continue;
            }
            double appeal = (course.price() == 0 ? 0.75 : 0.5) + rnd.nextDouble() * 0.25;
            int target = Math.min(studentCount, Math.max(4, (int) Math.round(studentCount * appeal)));
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < studentCount; i++) {
                order.add(i);
            }
            Collections.shuffle(order, rnd);
            int added = 0;
            for (int s : order) {
                if (added >= target) {
                    break;
                }
                int window = Math.min(students.get(s).joinedDaysAgo(), course.ageDays());
                if (window < 3) {
                    continue;
                }
                int enrolledDaysAgo = 2 + rnd.nextInt(window - 1);
                enrollments.add(enrollment(rnd, s, c, course, enrolledDaysAgo));
                added++;
            }
        }
        return new Plan(students, enrollments);
    }

    private static EnrollmentSpec enrollment(Random rnd, int s, int c, CourseInput course, int enrolledDaysAgo) {
        int total = course.lessonCount();
        double roll = rnd.nextDouble();
        double completeChance = total > 30 ? 0.08 : 0.28;
        int done;
        boolean partial = false;
        if (roll < completeChance) {
            done = total;
        } else if (roll < completeChance + 0.40) {
            done = Math.max(1, (int) Math.round(total * (0.4 + rnd.nextDouble() * 0.55)));
            done = Math.min(done, total - 1);
            partial = rnd.nextBoolean();
        } else if (roll < completeChance + 0.70) {
            done = Math.min(total - 1, 1 + (int) Math.round(total * rnd.nextDouble() * 0.35));
            partial = rnd.nextBoolean();
        } else {
            done = 0;
        }
        boolean paid = course.price() > 0;
        boolean referral = paid && rnd.nextDouble() < 0.15;
        String method = METHODS[rnd.nextInt(METHODS.length)];

        Integer rating = null;
        String comment = null;
        if (done * 10 >= total * 3 && rnd.nextDouble() < 0.75) {
            double r = rnd.nextDouble();
            rating = r < 0.55 ? 5 : r < 0.85 ? 4 : r < 0.95 ? 3 : 2;
            if (rnd.nextDouble() < 0.7) {
                String[] pool = rating >= 4 ? GOOD : rating == 3 ? OK : LOW;
                comment = pool[rnd.nextInt(pool.length)];
            }
        }
        return new EnrollmentSpec(s, c, enrolledDaysAgo, done, partial, paid, referral, method, rating, comment);
    }

    /** {@code avatarRnd} tách riêng để việc thêm avatar không làm đổi tên/email đã sinh trước đó (giữ tính idempotent). */
    private static List<StudentSpec> students(Random rnd, Random avatarRnd, int count) {
        List<StudentSpec> out = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        while (out.size() < count) {
            boolean female = rnd.nextBoolean();
            String family = FAMILY[rnd.nextInt(FAMILY.length)];
            String middle = (female ? MIDDLE_F : MIDDLE_M)[rnd.nextInt(8)];
            String given = (female ? GIVEN_F : GIVEN_M)[rnd.nextInt(20)];
            String fullName = family + " " + middle + " " + given;
            String base = ascii(given) + "." + ascii(family);
            String email = base + (80 + rnd.nextInt(20)) + "@example.com";
            if (!emails.add(email)) {
                continue;
            }
            out.add(new StudentSpec(fullName, email, 20 + rnd.nextInt(340), avatar(avatarRnd, female, email)));
        }
        return out;
    }

    /** Khoảng 30% không có avatar (chữ cái đầu), 30% ảnh chân dung, 40% hình vẽ nhiều phong cách — tên file tất định theo seed. */
    private static String avatar(Random rnd, boolean female, String email) {
        double r = rnd.nextDouble();
        if (r < 0.30) {
            return null;
        }
        if (r < 0.60) {
            return "https://randomuser.me/api/portraits/" + (female ? "women/" : "men/") + rnd.nextInt(90) + ".jpg";
        }
        String style = DRAWN_STYLES[rnd.nextInt(DRAWN_STYLES.length)];
        return "https://api.dicebear.com/9.x/" + style + "/svg?seed=" + email.substring(0, email.indexOf('@'));
    }

    static String ascii(String s) {
        String n = Normalizer.normalize(s.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase();
    }
}
