package com.lms.finance.controller;

import com.lms.finance.dto.ShareDto;
import com.lms.finance.service.ShareStatsService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Khóa của giảng viên + mã giới thiệu + thống kê đơn theo nguồn (11/11/2026, Giai đoạn D). Cần đăng nhập, chỉ INSTRUCTOR. */
@RestController
@RequestMapping("/api/v1/instructor/share")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorShareController {

    private final ShareStatsService shareStatsService;

    @GetMapping("/courses")
    public ResponseEntity<List<ShareDto.CourseShareRes>> courses(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shareStatsService.listForInstructor(principal.getName(), from, to));
    }
}
