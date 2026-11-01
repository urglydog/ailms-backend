package com.lms.dubbing.service;

import com.lms.common.enums.JobStatus;
import com.lms.dubbing.dto.AiJobDto.Res;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.repository.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC45 — danh sách job lồng tiếng cho Admin giám sát, lọc theo trạng thái. */
@Service
@RequiredArgsConstructor
public class AiJobAdminService {

    private final AiJobRepository aiJobRepository;

    @Transactional(readOnly = true)
    public Page<Res> getList(JobStatus status, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return aiJobRepository.search(status, normalizedSearch, pageable).map(this::mapToRes);
    }

    private Res mapToRes(AiJob job) {
        return new Res(
                job.getId(),
                job.getLesson().getId(),
                job.getLesson().getTitle(),
                job.getTargetLanguage(),
                job.getStatus().name(),
                job.getTotalChunks(),
                job.getDoneChunks(),
                job.progressPercent(),
                job.getRetryCount(),
                job.getErrorMessage(),
                job.getCreatedAt()
        );
    }
}
