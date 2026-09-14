package com.lms.catalog.service;

import com.lms.catalog.dto.CoursePublicDto.DetailRes;
import com.lms.catalog.dto.CoursePublicDto.PlayerRes;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra BR-ROLE-03: chỉ khóa PUBLISHED được duyệt công khai. */
@ExtendWith(MockitoExtension.class)
class CoursePublicServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private AudioTrackRepository audioTrackRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private CoursePublicService coursePublicService;

    @Test
    void getBySlug_throwsNotFound_whenMissingOrNotPublished() {
        when(courseRepository.findBySlugAndStatus("khong-ton-tai", CourseStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> coursePublicService.getBySlug("khong-ton-tai"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBySlug_returnsDetail_whenPublished() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Lap trinh Web");
        category.setSlug("lap-trinh-web");

        var instructor = new com.lms.auth.entity.User();
        instructor.setFullName("Tran Thanh Ha");

        Course course = new Course();
        course.setId(5L);
        course.setTitle("Khoa hoc test");
        course.setSlug("khoa-hoc-test");
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setAvgRating(BigDecimal.valueOf(4.5));
        course.setStatus(CourseStatus.PUBLISHED);

        when(courseRepository.findBySlugAndStatus("khoa-hoc-test", CourseStatus.PUBLISHED))
                .thenReturn(Optional.of(course));
        lenient().when(chapterRepository.findByCourseIdOrderByDisplayOrderAsc(5L)).thenReturn(List.of());
        lenient().when(courseReviewRepository.countByCourse_IdAndIsHiddenFalse(5L)).thenReturn(2L);
        lenient().when(audioTrackRepository.findAvailableLanguagesByCourse(5L)).thenReturn(List.of());

        DetailRes result = coursePublicService.getBySlug("khoa-hoc-test");

        assertThat(result.title()).isEqualTo("Khoa hoc test");
        assertThat(result.reviewCount()).isEqualTo(2L);
        assertThat(result.categorySlug()).isEqualTo("lap-trinh-web");
    }

    @Test
    void search_mapsPriceTypeFreeToIsFreeTrue() {
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        coursePublicService.search(null, null, null, "free", null, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<Boolean> isFreeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(courseRepository).searchPublic(
                isNull(), isNull(), isNull(), isFreeCaptor.capture(), isNull(), isNull(), isNull(), any(Pageable.class));
        assertThat(isFreeCaptor.getValue()).isTrue();
    }

    @Test
    void search_mapsPriceTypePaidToIsFreeFalse() {
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        coursePublicService.search(null, null, null, "paid", null, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<Boolean> isFreeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(courseRepository).searchPublic(
                isNull(), isNull(), isNull(), isFreeCaptor.capture(), isNull(), isNull(), isNull(), any(Pageable.class));
        assertThat(isFreeCaptor.getValue()).isFalse();
    }

    @Test
    void search_mapsPriceTypeAllToNullIsFree() {
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        coursePublicService.search("react", "lap-trinh-web", "beginner", "all", null, null, null, PageRequest.of(0, 10));

        verify(courseRepository).searchPublic(
                eq("react"), eq("lap-trinh-web"), eq("BEGINNER"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void search_mapsMinRating_toBigDecimal() {
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        coursePublicService.search(null, null, null, null, 4.5, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<BigDecimal> minRatingCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(courseRepository).searchPublic(
                isNull(), isNull(), isNull(), isNull(), minRatingCaptor.capture(), isNull(), isNull(), any(Pageable.class));
        assertThat(minRatingCaptor.getValue()).isEqualByComparingTo("4.5");
    }

    @Test
    void search_mapsDurationBucket_toSecondsRange() {
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        coursePublicService.search(null, null, null, null, null, "1-3", null, PageRequest.of(0, 10));

        verify(courseRepository).searchPublic(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(3600), eq(10800), any(Pageable.class));
    }

    private Course courseWithRatingAndTitle(long id, String title, double avgRating) {
        Category category = new Category();
        category.setId(1L);
        category.setName("Cat");
        category.setSlug("cat");
        var instructor = new com.lms.auth.entity.User();
        instructor.setFullName("GV");

        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setAvgRating(BigDecimal.valueOf(avgRating));
        return course;
    }

    @Test
    void search_sortByRating_ordersHighestFirst() {
        Course low = courseWithRatingAndTitle(1L, "Khoa A", 3.0);
        Course high = courseWithRatingAndTitle(2L, "Khoa B", 4.8);
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(low, high)));

        var result = coursePublicService.search(null, null, null, null, null, null, "rating", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting("id").containsExactly(2L, 1L);
    }

    @Test
    void search_sortByReviews_ordersMostReviewedFirst() {
        Course fewReviews = courseWithRatingAndTitle(1L, "Khoa A", 4.0);
        Course manyReviews = courseWithRatingAndTitle(2L, "Khoa B", 4.0);
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(fewReviews, manyReviews)));
        lenient().when(courseReviewRepository.countByCourse_IdAndIsHiddenFalse(1L)).thenReturn(1L);
        lenient().when(courseReviewRepository.countByCourse_IdAndIsHiddenFalse(2L)).thenReturn(9L);

        var result = coursePublicService.search(null, null, null, null, null, null, "reviews", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting("id").containsExactly(2L, 1L);
    }

    @Test
    void search_sortByRelevance_prefixMatchRanksAboveMiddleMatch() {
        Course middleMatch = courseWithRatingAndTitle(1L, "Nhap mon React co ban", 4.0);
        Course prefixMatch = courseWithRatingAndTitle(2L, "React nang cao", 4.0);
        when(courseRepository.searchPublic(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(middleMatch, prefixMatch)));

        var result = coursePublicService.search("react", null, null, null, null, null, "relevance", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting("id").containsExactly(2L, 1L);
    }

    /** UC11 — Học thử Preview (BR-ENROLL-02): Guest/Student chưa sở hữu chỉ xem được bài Preview. */
    @Test
    void getLessonForPlayback_returnsVideoData_whenLessonIsPreviewAndCoursePublished() {
        Lesson lesson = previewLessonOf(CourseStatus.PUBLISHED);
        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));

        PlayerRes result = coursePublicService.getLessonForPlayback(30L);

        assertThat(result.videoSource()).isEqualTo("YOUTUBE");
        assertThat(result.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=abc12345678");
        assertThat(result.courseSlug()).isEqualTo("khoa-hoc-test");
    }

    @Test
    void getLessonForPlayback_throwsAccessDenied_whenLessonIsNotPreview() {
        Lesson lesson = previewLessonOf(CourseStatus.PUBLISHED);
        lesson.setIsPreview(false);
        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> coursePublicService.getLessonForPlayback(30L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void getLessonForPlayback_throwsNotFound_whenCourseNotPublished() {
        Lesson lesson = previewLessonOf(CourseStatus.PENDING);
        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> coursePublicService.getLessonForPlayback(30L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getLessonForPlayback_throwsNotFound_whenLessonMissing() {
        when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coursePublicService.getLessonForPlayback(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Lesson previewLessonOf(CourseStatus courseStatus) {
        Course course = new Course();
        course.setId(5L);
        course.setTitle("Khoa hoc test");
        course.setSlug("khoa-hoc-test");
        course.setStatus(courseStatus);

        Chapter chapter = new Chapter();
        chapter.setId(20L);
        chapter.setCourse(course);

        Lesson lesson = new Lesson();
        lesson.setId(30L);
        lesson.setTitle("Bai hoc thu");
        lesson.setChapter(chapter);
        lesson.setIsPreview(true);
        lesson.setVideoSource("YOUTUBE");
        lesson.setVideoUrl("https://www.youtube.com/watch?v=abc12345678");
        lesson.setYoutubeId("abc12345678");
        lesson.setDurationSec(300);
        return lesson;
    }
}
