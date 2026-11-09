package com.lms.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.enrollment.service.ResumePointResolver.LatestProgress;
import com.lms.enrollment.service.ResumePointResolver.ResumePoint;
import java.util.List;
import org.junit.jupiter.api.Test;

/** "Học tiếp" ở trang Khóa học của tôi: dẫn đúng bài đang học dở, không phải luôn bài đầu. */
class ResumePointResolverTest {

    private static final List<Long> LESSONS = List.of(11L, 12L, 13L, 14L);

    @Test
    void noProgress_resumesAtFirstLessonFromStart() {
        ResumePoint point = ResumePointResolver.resolve(LESSONS, null);

        assertThat(point.lessonId()).isEqualTo(11L);
        assertThat(point.positionSec()).isZero();
    }

    @Test
    void unfinishedLesson_resumesThereAtSavedPosition() {
        ResumePoint point = ResumePointResolver.resolve(LESSONS, new LatestProgress(13L, 245, false));

        assertThat(point.lessonId()).isEqualTo(13L);
        assertThat(point.positionSec()).isEqualTo(245);
    }

    @Test
    void completedLesson_resumesAtNextLessonFromStart() {
        ResumePoint point = ResumePointResolver.resolve(LESSONS, new LatestProgress(12L, 600, true));

        assertThat(point.lessonId()).isEqualTo(13L);
        assertThat(point.positionSec()).isZero();
    }

    @Test
    void completedLastLesson_staysOnLastLessonFromStart() {
        ResumePoint point = ResumePointResolver.resolve(LESSONS, new LatestProgress(14L, 300, true));

        assertThat(point.lessonId()).isEqualTo(14L);
        assertThat(point.positionSec()).isZero();
    }

    @Test
    void progressOnLessonNoLongerInCourse_fallsBackToFirstLesson() {
        ResumePoint point = ResumePointResolver.resolve(LESSONS, new LatestProgress(999L, 50, false));

        assertThat(point.lessonId()).isEqualTo(11L);
        assertThat(point.positionSec()).isZero();
    }

    @Test
    void courseWithoutLessons_hasNoResumeLesson() {
        ResumePoint point = ResumePointResolver.resolve(List.of(), null);

        assertThat(point.lessonId()).isNull();
        assertThat(point.positionSec()).isZero();
    }
}
