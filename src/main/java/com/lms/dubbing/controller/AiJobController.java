package com.lms.dubbing.controller;

import com.lms.common.enums.JobStatus;
import com.lms.dubbing.dto.AiJobDto.Res;
import com.lms.dubbing.dto.DubbingDto;
import com.lms.dubbing.service.AiJobAdminService;
import com.lms.dubbing.service.DubbingRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC45 — Admin giám sát hàng đợi lồng tiếng: danh sách + thử lại job lỗi. */
@RestController
@RequestMapping("/api/v1/admin/dubbing-jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiJobController {

    private final AiJobAdminService aiJobAdminService;
    private final DubbingRequestService dubbingRequestService;

    @GetMapping
    public ResponseEntity<Page<Res>> getList(
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(aiJobAdminService.getList(status, pageable));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<DubbingDto.Res> retry(@PathVariable Long id) {
        return ResponseEntity.ok(dubbingRequestService.retryJob(id));
    }
}
