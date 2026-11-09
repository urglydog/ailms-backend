package com.lms.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.demo.DemoDataPlanner.CourseInput;
import com.lms.demo.DemoDataPlanner.EnrollmentSpec;
import com.lms.demo.DemoDataPlanner.Plan;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoDataPlannerTest {

    private static final List<CourseInput> COURSES = List.of(
            new CourseInput(11, 0, 330, true, 14_000),
            new CourseInput(38, 299_000, 270, true, 18_000),
            new CourseInput(63, 199_000, 330, true, 50_000),
            new CourseInput(57, 249_000, 30, false, 43_000));

    @Test
    void sameSeedGivesSamePlan() {
        Plan a = DemoDataPlanner.plan(7L, 40, COURSES);
        Plan b = DemoDataPlanner.plan(7L, 40, COURSES);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void createsRequestedNumberOfStudentsWithUniqueEmails() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        assertThat(plan.students()).hasSize(40);
        Set<String> emails = new HashSet<>();
        plan.students().forEach(s -> {
            assertThat(s.email()).matches("[a-z0-9.]+@[a-z.]+");
            assertThat(emails.add(s.email())).as("email %s duplicated", s.email()).isTrue();
        });
    }

    @Test
    void avatarsMixPhotosIllustrationsAndNone() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        long none = plan.students().stream().filter(s -> s.avatarUrl() == null).count();
        long photos = plan.students().stream().filter(s -> s.avatarUrl() != null && s.avatarUrl().contains("randomuser.me")).count();
        long drawn = plan.students().stream().filter(s -> s.avatarUrl() != null && s.avatarUrl().contains("dicebear.com")).count();
        assertThat(none).isPositive();
        assertThat(photos).isPositive();
        assertThat(drawn).isPositive();
        assertThat(none + photos + drawn).isEqualTo(40);
    }

    @Test
    void neverEnrollsTwiceAndNeverBeforeStudentOrCourseExisted() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        Set<String> pairs = new HashSet<>();
        for (EnrollmentSpec e : plan.enrollments()) {
            assertThat(pairs.add(e.student() + "-" + e.course())).isTrue();
            assertThat(e.enrolledDaysAgo()).isLessThanOrEqualTo(plan.students().get(e.student()).joinedDaysAgo());
            assertThat(e.enrolledDaysAgo()).isLessThanOrEqualTo(COURSES.get(e.course()).ageDays());
        }
    }

    @Test
    void unpublishedCoursesHaveNoEnrollments() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        assertThat(plan.enrollments()).noneMatch(e -> e.course() == 3);
    }

    @Test
    void paidFlagMatchesPriceAndProgressIsWithinLessonCount() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        assertThat(plan.enrollments()).isNotEmpty();
        for (EnrollmentSpec e : plan.enrollments()) {
            CourseInput c = COURSES.get(e.course());
            assertThat(e.paid()).isEqualTo(c.price() > 0);
            assertThat(e.completedLessons()).isBetween(0, c.lessonCount());
        }
    }

    @Test
    void ratingsOnlyFromStudentsWhoWatchedEnoughAndStayInRange() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        long rated = plan.enrollments().stream().filter(e -> e.rating() != null).count();
        assertThat(rated).isPositive();
        for (EnrollmentSpec e : plan.enrollments()) {
            if (e.rating() != null) {
                assertThat(e.rating()).isBetween(1, 5);
                assertThat(e.completedLessons() * 10).isGreaterThanOrEqualTo(COURSES.get(e.course()).lessonCount() * 3);
            }
        }
    }

    @Test
    void mixOfCompletedAndInProgressLearners() {
        Plan plan = DemoDataPlanner.plan(7L, 40, COURSES);
        long completed = plan.enrollments().stream()
                .filter(e -> e.completedLessons() == COURSES.get(e.course()).lessonCount()).count();
        long partial = plan.enrollments().stream()
                .filter(e -> e.completedLessons() > 0 && e.completedLessons() < COURSES.get(e.course()).lessonCount())
                .count();
        assertThat(completed).isPositive();
        assertThat(partial).isGreaterThan(completed);
    }
}
