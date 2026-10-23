package com.lms.dubbing.service;

import com.lms.catalog.entity.Lesson;
import com.lms.common.enums.JobStatus;
import com.lms.dubbing.dto.AiJobDto.Res;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.repository.AiJobRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC45 — danh sách job cho Admin, có/không lọc theo trạng thái và tìm kiếm (23/10/2026, mở rộng). */
@ExtendWith(MockitoExtension.class)
class AiJobAdminServiceTest {

    @Mock
    private AiJobRepository aiJobRepository;

    @InjectMocks
    private AiJobAdminService aiJobAdminService;

    private AiJob job(long id, JobStatus status) {
        Lesson lesson = new Lesson();
        lesson.setId(30L);
        lesson.setTitle("Bài 1");

        AiJob job = new AiJob();
        job.setId(id);
        job.setLesson(lesson);
        job.setTargetLanguage("en-US");
        job.setStatus(status);
        job.setTotalChunks(2);
        job.setDoneChunks(1);
        job.setRetryCount(0);
        return job;
    }

    @Test
    void getList_withoutFilters_callsSearchWithNulls() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AiJob> page = new PageImpl<>(List.of(job(1L, JobStatus.PROCESSING)));
        when(aiJobRepository.search(null, null, pageable)).thenReturn(page);

        Page<Res> result = aiJobAdminService.getList(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        Res res = result.getContent().get(0);
        assertThat(res.lessonId()).isEqualTo(30L);
        assertThat(res.lessonTitle()).isEqualTo("Bài 1");
        assertThat(res.status()).isEqualTo("PROCESSING");
        assertThat(res.progressPercent()).isEqualTo(50);
    }

    @Test
    void getList_withStatusFilter_passesStatusThrough() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AiJob> page = new PageImpl<>(List.of(job(2L, JobStatus.FAILED)));
        when(aiJobRepository.search(JobStatus.FAILED, null, pageable)).thenReturn(page);

        Page<Res> result = aiJobAdminService.getList(JobStatus.FAILED, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("FAILED");
        verify(aiJobRepository).search(JobStatus.FAILED, null, pageable);
    }

    @Test
    void getList_withBlankSearch_normalizesToNull() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AiJob> page = new PageImpl<>(List.of());
        when(aiJobRepository.search(null, null, pageable)).thenReturn(page);

        aiJobAdminService.getList(null, "   ", pageable);

        verify(aiJobRepository).search(null, null, pageable);
    }

    @Test
    void getList_withSearch_trimsAndPassesThrough() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AiJob> page = new PageImpl<>(List.of(job(3L, JobStatus.COMPLETED)));
        when(aiJobRepository.search(null, "Bài 1", pageable)).thenReturn(page);

        Page<Res> result = aiJobAdminService.getList(null, " Bài 1 ", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(aiJobRepository).search(null, "Bài 1", pageable);
    }
}
