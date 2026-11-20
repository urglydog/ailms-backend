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

    /**
     * Danh sách job cho admin. {@code view}: {@code ATTENTION} = chỉ job lỗi chưa có bản thay thế thành công; {@code null} = theo {@code status}. Mặc định giao diện mở ở
     * "Cần xử lý" nên job lỗi cũ đã được bản sau thay thế không làm rối màn hình.
     */
    @Transactional(readOnly = true)
    public Page<Res> getList(JobStatus status, String search, String view, java.time.LocalDate from, java.time.LocalDate to, Pageable pageable) {
        String kw = (search == null || search.isBlank()) ? null : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        long kwId = search != null && search.trim().matches("\\d{1,18}") ? Long.parseLong(search.trim()) : -1L;
        boolean attention = "ATTENTION".equalsIgnoreCase(view);
        return aiJobRepository.searchAdmin(attention ? null : status, attention, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), kw, kwId, pageable).map(this::mapToRes);
    }

    private Res mapToRes(AiJob job) {
        boolean superseded = job.getStatus() == JobStatus.FAILED
                && aiJobRepository.existsByLesson_IdAndTargetLanguageAndStatus(job.getLesson().getId(), job.getTargetLanguage(), JobStatus.COMPLETED);
        var requester = job.getRequestedBy();
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
                job.getCreatedAt(),
                job.getLesson().getChapter().getCourse().getTitle(),
                requester == null ? null : requester.getFullName(),
                requester == null ? null : requester.getEmail(),
                job.getFinishedAt(),
                superseded,
                job.getStatus() == JobStatus.FAILED && job.canRetry() && !superseded
        );
    }
}
