package com.lms.dubbing.controller;

import com.lms.dubbing.dto.DubbingDto.RequestReq;
import com.lms.dubbing.dto.DubbingDto.Res;
import com.lms.dubbing.service.DubbingRequestService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC18 — kích hoạt lồng tiếng AI cho một bài học. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class DubbingController {

    private final DubbingRequestService dubbingRequestService;

    @PostMapping("/api/v1/lessons/{lessonId}/dubbing")
    public ResponseEntity<Res> requestDubbing(
            Principal principal, @PathVariable Long lessonId, @Valid @RequestBody RequestReq req) {
        return ResponseEntity.ok(dubbingRequestService.requestDubbing(principal.getName(), lessonId, req));
    }
}
