package com.lms.chat.controller;

import com.lms.chat.dto.TutorInsightDto.CourseInsightRes;
import com.lms.chat.dto.TutorInsightDto.SummaryRes;
import com.lms.chat.service.TutorInsightService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Insight từ Gia sư AI (26/11/2026). Chỉ chủ sở hữu khoá học xem được (kiểm ở service) —
 * vai trò STUDENT vẫn được qua cổng vì học viên có thể mở kênh giảng viên.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/instructor/courses/{courseId}/tutor-insights")
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class TutorInsightController {

    private final TutorInsightService insightService;

    @GetMapping
    public ResponseEntity<CourseInsightRes> insights(Principal principal, @PathVariable Long courseId,
                                                     @RequestParam(required = false) Long lessonId) {
        return ResponseEntity.ok(insightService.insights(principal.getName(), courseId, lessonId));
    }

    /** Yêu cầu sinh lại tóm tắt AI — xử lý nền, trả 202 kèm trạng thái hiện tại. */
    @PostMapping("/summary")
    public ResponseEntity<SummaryRes> refreshSummary(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.accepted().body(insightService.requestSummary(principal.getName(), courseId));
    }
}
