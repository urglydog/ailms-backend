package com.lms.dubbing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.common.enums.JobStatus;
import com.lms.dubbing.dto.AiJobDto.Res;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.repository.AiJobRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** UC45 — danh sách job cho Admin: lọc trạng thái/ngày/"cần xử lý", tìm kiếm, và đánh dấu job lỗi đã được bản sau thay thế. */
@ExtendWith(MockitoExtension.class)
class AiJobAdminServiceTest {

    @Mock
    private AiJobRepository aiJobRepository;

    @InjectMocks
    private AiJobAdminService aiJobAdminService;

    private AiJob job(long id, JobStatus status) {
        Course course = new Course();
        course.setTitle("Khóa Java");
        Chapter chapter = new Chapter();
        chapter.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(30L);
        lesson.setTitle("Bài 1");
        lesson.setChapter(chapter);
        User requester = new User();
        requester.setFullName("Giảng Viên");
        requester.setEmail("gv@gmail.com");

        AiJob job = new AiJob();
        job.setId(id);
        job.setLesson(lesson);
        job.setRequestedBy(requester);
        job.setTargetLanguage("en-US");
        job.setStatus(status);
        job.setTotalChunks(2);
        job.setDoneChunks(1);
        job.setRetryCount(0);
        job.setFinishedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        return job;
    }

    @Test
    void getList_withoutFilters_mapsCourseRequesterAndProgress() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AiJob> page = new PageImpl<>(List.of(job(1L, JobStatus.PROCESSING)));
        when(aiJobRepository.searchAdmin(isNull(), eq(false), isNull(), isNull(), isNull(), eq(-1L), eq(pageable))).thenReturn(page);

        Page<Res> result = aiJobAdminService.getList(null, null, null, null, null, pageable);

        Res res = result.getContent().get(0);
        assertThat(res.lessonId()).isEqualTo(30L);
        assertThat(res.courseTitle()).isEqualTo("Khóa Java");
        assertThat(res.requestedByEmail()).isEqualTo("gv@gmail.com");
        assertThat(res.status()).isEqualTo("PROCESSING");
        assertThat(res.progressPercent()).isEqualTo(50);
        assertThat(res.superseded()).isFalse();
        assertThat(res.retryable()).isFalse();
    }

    @Test
    void getList_normalisesSearchDetectsNumericIdAndPassesDateRange() {
        Pageable pageable = PageRequest.of(0, 10);
        when(aiJobRepository.searchAdmin(any(), eq(false), any(), any(), any(), anyLong(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        aiJobAdminService.getList(JobStatus.COMPLETED, " Java ", null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), pageable);
        aiJobAdminService.getList(null, "  ", null, null, null, pageable);
        aiJobAdminService.getList(null, "2288", null, null, null, pageable);

        verify(aiJobRepository).searchAdmin(eq(JobStatus.COMPLETED), eq(false), eq(LocalDate.of(2026, 11, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 12, 1).atStartOfDay()), eq("%java%"), eq(-1L), eq(pageable));
        verify(aiJobRepository).searchAdmin(isNull(), eq(false), isNull(), isNull(), isNull(), eq(-1L), eq(pageable));
        verify(aiJobRepository).searchAdmin(isNull(), eq(false), isNull(), isNull(), eq("%2288%"), eq(2288L), eq(pageable));
    }

    @Test
    void getList_attentionViewIgnoresStatusAndOnlyAsksForFailedWithoutASuccessfulReplacement() {
        Pageable pageable = PageRequest.of(0, 10);
        when(aiJobRepository.searchAdmin(any(), eq(true), any(), any(), any(), anyLong(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        aiJobAdminService.getList(JobStatus.COMPLETED, null, "ATTENTION", null, null, pageable);

        verify(aiJobRepository).searchAdmin(isNull(), eq(true), isNull(), isNull(), isNull(), eq(-1L), eq(pageable));
    }

    @Test
    void getList_failedJobThatWasLaterReplacedByASuccessIsMarkedSupersededAndNotRetryable() {
        Pageable pageable = PageRequest.of(0, 10);
        AiJob replaced = job(7L, JobStatus.FAILED);
        AiJob stillBroken = job(8L, JobStatus.FAILED);
        stillBroken.getLesson().setId(31L);
        when(aiJobRepository.searchAdmin(any(), eq(false), any(), any(), any(), anyLong(), eq(pageable))).thenReturn(new PageImpl<>(List.of(replaced, stillBroken)));
        when(aiJobRepository.existsByLesson_IdAndTargetLanguageAndStatus(30L, "en-US", JobStatus.COMPLETED)).thenReturn(true);
        when(aiJobRepository.existsByLesson_IdAndTargetLanguageAndStatus(31L, "en-US", JobStatus.COMPLETED)).thenReturn(false);

        List<Res> rows = aiJobAdminService.getList(JobStatus.FAILED, null, null, null, null, pageable).getContent();

        assertThat(rows.get(0).superseded()).isTrue();
        assertThat(rows.get(0).retryable()).isFalse();
        assertThat(rows.get(1).superseded()).isFalse();
        assertThat(rows.get(1).retryable()).isTrue();
    }

    @Test
    void getList_failedJobOutOfRetriesIsNotRetryable() {
        Pageable pageable = PageRequest.of(0, 10);
        AiJob exhausted = job(9L, JobStatus.FAILED);
        exhausted.setRetryCount(3);
        when(aiJobRepository.searchAdmin(any(), eq(false), any(), any(), any(), anyLong(), eq(pageable))).thenReturn(new PageImpl<>(List.of(exhausted)));

        Res res = aiJobAdminService.getList(JobStatus.FAILED, null, null, null, null, pageable).getContent().get(0);

        assertThat(res.superseded()).isFalse();
        assertThat(res.retryable()).isFalse();
    }

    @Test
    void summary_countsComeFromTheJobTableNotFromWorkerCache() {
        when(aiJobRepository.countByStatus(JobStatus.PENDING)).thenReturn(2L);
        when(aiJobRepository.countByStatus(JobStatus.PROCESSING)).thenReturn(1L);
        when(aiJobRepository.countByStatus(JobStatus.COMPLETED)).thenReturn(10L);
        when(aiJobRepository.countNeedingAttention()).thenReturn(1L);

        AiJobAdminService.Summary summary = aiJobAdminService.getSummary();

        assertThat(summary.pending()).isEqualTo(2L);
        assertThat(summary.processing()).isEqualTo(1L);
        assertThat(summary.completed()).isEqualTo(10L);
        assertThat(summary.needsAttention()).isEqualTo(1L);
    }
}
