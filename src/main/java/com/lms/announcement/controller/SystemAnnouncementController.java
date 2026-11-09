package com.lms.announcement.controller;

import com.lms.announcement.dto.SystemAnnouncementDto.BannerRes;
import com.lms.announcement.dto.SystemAnnouncementDto.CreateReq;
import com.lms.announcement.dto.SystemAnnouncementDto.Res;
import com.lms.announcement.service.SystemAnnouncementService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SystemAnnouncementController {

    private final SystemAnnouncementService announcementService;

    /** Admin soạn + gửi thông báo hệ thống — trả về NGAY (job phát tán chạy nền qua Redis, xem
     * SystemAnnouncementService), không chờ gửi hết cho toàn bộ người nhận. */
    @PostMapping("/api/v1/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> create(Principal principal, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(announcementService.create(principal.getName(), req));
    }

    @GetMapping("/api/v1/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Res>> listAll() {
        return ResponseEntity.ok(announcementService.listAll());
    }

    /** Banner cố định đầu trang — PUBLIC, không cần đăng nhập (đăng ký ở
     * {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}): thông báo "Bảo trì hệ thống" (severity HIGH)
     * cũng phải hiện được cho khách vãng lai chưa đăng nhập. {@code null} nghĩa là không có banner
     * nào đang hoạt động. */
    @GetMapping("/api/v1/system-announcements/banner")
    public ResponseEntity<BannerRes> getActiveBanner() {
        return ResponseEntity.ok(announcementService.getActiveBanner());
    }
}
