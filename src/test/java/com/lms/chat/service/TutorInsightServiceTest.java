package com.lms.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorInsightDto.SummaryRes;
import com.lms.chat.entity.CourseTutorInsight;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.chat.repository.CourseTutorInsightRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.common.exception.AccessDeniedDomainException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorInsightServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private CourseTutorInsightRepository insightRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    private TutorInsightService service;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new TutorInsightService(courseRepository, lessonRepository, chatMessageRepository,
                insightRepository, restTemplate, aiWorkerConfig, new ObjectMapper());
        User owner = new User();
        owner.setEmail("owner@lms.local");
        course = new Course();
        course.setId(5L);
        course.setTitle("Java");
        course.setInstructor(owner);
        lenient().when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
    }

    @Test
    void nonOwner_isDenied() {
        assertThatThrownBy(() -> service.insights("other@lms.local", 5L, null))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.requestSummary("other@lms.local", 5L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void owner_emptyCourse_returnsEmptyInsight_withNoneSummary() {
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(5L)).thenReturn(List.of());
        when(chatMessageRepository.findQuestionsForCourseInsight(eq(5L), any(), anyInt())).thenReturn(List.of());
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        var res = service.insights("owner@lms.local", 5L, null);

        assertThat(res.totalQuestions()).isZero();
        assertThat(res.lessons()).isEmpty();
        assertThat(res.summary().status()).isEqualTo("NONE");
    }

    @Test
    void insights_reuseAggregateScanWithinTtl() {
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(5L)).thenReturn(List.of());
        when(chatMessageRepository.findQuestionsForCourseInsight(eq(5L), any(), anyInt())).thenReturn(List.of());
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        service.insights("owner@lms.local", 5L, null);
        service.insights("owner@lms.local", 5L, null);

        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.times(1))
                .findQuestionsForCourseInsight(eq(5L), any(), anyInt());
    }

    @Test
    void requestSummary_queuesPending_butRespectsCooldown() {
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.empty());
        SummaryRes queued = service.requestSummary("owner@lms.local", 5L);
        assertThat(queued.status()).isEqualTo("PENDING");

        CourseTutorInsight fresh = new CourseTutorInsight();
        fresh.setCourseId(5L);
        fresh.setStatus(CourseTutorInsight.READY);
        fresh.setGeneratedAt(LocalDateTime.now().minusMinutes(1));
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.of(fresh));
        assertThat(service.requestSummary("owner@lms.local", 5L).status()).isEqualTo("READY");
        verify(insightRepository, never()).save(fresh);
    }

    @Test
    void requestSummary_afterRecentFailure_respectsCooldown_andExposesRetryAfter() {
        CourseTutorInsight failed = new CourseTutorInsight();
        failed.setCourseId(5L);
        failed.setStatus(CourseTutorInsight.FAILED);
        failed.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.of(failed));

        SummaryRes res = service.requestSummary("owner@lms.local", 5L);

        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.retryAfter()).isAfter(LocalDateTime.now());
        verify(insightRepository, never()).save(failed);
    }

    @Test
    void requestSummary_afterOldFailure_requeues() {
        CourseTutorInsight failed = new CourseTutorInsight();
        failed.setCourseId(5L);
        failed.setStatus(CourseTutorInsight.FAILED);
        failed.setUpdatedAt(LocalDateTime.now().minusMinutes(11));
        when(insightRepository.findByCourseId(5L)).thenReturn(Optional.of(failed));

        SummaryRes res = service.requestSummary("owner@lms.local", 5L);

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.retryAfter()).isNull();
    }

    @Test
    void generate_withoutQuestions_skipsLlm() {
        when(lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(5L)).thenReturn(List.of());
        when(chatMessageRepository.findQuestionsForCourseInsight(eq(5L), any(), anyInt())).thenReturn(List.of());
        CourseTutorInsight row = new CourseTutorInsight();
        row.setCourseId(5L);

        service.generate(row);

        assertThat(row.getStatus()).isEqualTo(CourseTutorInsight.READY);
        assertThat(row.getSummary()).isNull();
        verifyNoInteractions(restTemplate);
    }
}
