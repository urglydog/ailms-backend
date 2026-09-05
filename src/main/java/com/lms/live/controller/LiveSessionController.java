package com.lms.live.controller;

import com.lms.live.dto.LiveSessionDto.CreateReq;
import com.lms.live.dto.LiveSessionDto.Res;
import com.lms.live.dto.LiveSessionDto.StartRes;
import com.lms.live.service.LiveSessionService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC50 — tạo/lên lịch, bắt đầu, kết thúc phiên Live (chỉ giảng viên sở hữu khóa học).
 *
 * <p>Endpoint GET công khai cho người XEM (Student/Guest/Admin, kiểm quyền BR-LIVE-01) thuộc
 * {@code LiveViewController} của F11.2 — không thêm vào đây (xem "Ranh giới file" ở
 * {@code doc/FEATURE_ASSIGNMENT.md} mục F11.1/F11.2).
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequestMapping("/api/v1/live-sessions")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @PostMapping
    public ResponseEntity<Res> create(Principal principal, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(liveSessionService.create(principal.getName(), req));
    }

    /** Danh sách phiên của chính giảng viên đang đăng nhập — dựng trang {@code app/instructor/live/}. */
    @GetMapping
    public ResponseEntity<List<Res>> listMine(Principal principal) {
        return ResponseEntity.ok(liveSessionService.listMine(principal.getName()));
    }

    @GetMapping("/{sessionId:\\d+}")
    public ResponseEntity<Res> getOwned(Principal principal, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveSessionService.getOwned(principal.getName(), sessionId));
    }

    /** F11.9 mở rộng — ảnh riêng cho buổi live (không bắt buộc, xem `LiveSessionService.uploadThumbnail`). */
    @PostMapping(value = "/{sessionId:\\d+}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Res> uploadThumbnail(
            Principal principal, @PathVariable Long sessionId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(liveSessionService.uploadThumbnail(principal.getName(), sessionId, file));
    }

    @PostMapping("/{sessionId:\\d+}/start")
    public ResponseEntity<StartRes> start(Principal principal, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveSessionService.start(principal.getName(), sessionId));
    }

    @PostMapping("/{sessionId:\\d+}/end")
    public ResponseEntity<Res> end(Principal principal, @PathVariable Long sessionId) {
        return ResponseEntity.ok(liveSessionService.end(principal.getName(), sessionId));
    }
}
