package com.lms.auth.controller;

import com.lms.auth.dto.InstructorRequestDto.*;
import com.lms.auth.service.InstructorRequestService;
import com.lms.common.enums.RequestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor-requests")
@RequiredArgsConstructor
public class InstructorRequestController {

    private final InstructorRequestService service;

    /**
     * Học viên gửi yêu cầu nâng cấp lên Giảng viên (UC07)
     */
    @PostMapping
    public ResponseEntity<Res> createRequest(Principal principal, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(service.createRequest(principal.getName(), req));
    }

    /**
     * Admin lấy danh sách các yêu cầu theo trạng thái (UC41)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Res>> getRequestsByStatus(
            @RequestParam(defaultValue = "PENDING") RequestStatus status) {
        return ResponseEntity.ok(service.getRequestsByStatus(status));
    }

    /**
     * Admin duyệt / từ chối yêu cầu (UC41)
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> updateStatus(
            @PathVariable Long id, 
            @Valid @RequestBody UpdateStatusReq req) {
        return ResponseEntity.ok(service.updateStatus(id, req));
    }
}
