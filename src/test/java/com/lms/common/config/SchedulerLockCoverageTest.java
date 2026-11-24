package com.lms.common.config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hiệu năng đợt 6 (A2): chốt chặn — job {@code @Scheduled} mới thêm vào phải có {@code @SchedulerLock}
 * (nếu không, chạy nhiều backend sẽ chạy trùng), trừ các job cố ý chạy trên mọi instance.
 */
class SchedulerLockCoverageTest {

    /** Job phải chạy trên MỌI instance hoặc đã an toàn nhờ RPOP nguyên tử của Redis. */
    private static final Set<String> INTENTIONALLY_UNLOCKED = Set.of(
            "TutorSecurityService.refreshPatterns",
            "SystemAnnouncementService.consumeQueue",
            "WishlistPriceDropService.consumeQueue");

    @Test
    void everyScheduledJobHasADistributedLockUnlessWhitelisted() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        List<String> missing = new ArrayList<>();
        Set<String> lockNames = new HashSet<>();
        int scheduled = 0;
        for (var bd : scanner.findCandidateComponents("com.lms")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            for (Method m : type.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Scheduled.class)) continue;
                scheduled++;
                String id = type.getSimpleName() + "." + m.getName();
                SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
                if (lock == null) {
                    if (!INTENTIONALLY_UNLOCKED.contains(id)) missing.add(id);
                } else {
                    assertThat(lockNames.add(lock.name())).as("tên khóa trùng: " + lock.name()).isTrue();
                }
            }
        }

        assertThat(scheduled).isGreaterThanOrEqualTo(19);
        assertThat(missing).as("job @Scheduled thiếu @SchedulerLock").isEmpty();
    }
}
