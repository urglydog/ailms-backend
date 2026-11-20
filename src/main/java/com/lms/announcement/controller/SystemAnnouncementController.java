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

    /** Lịch sử thông báo cho admin: tìm theo tiêu đề/nội dung, phân trang (30 dòng/trang mặc định). */
    @GetMapping("/api/v1/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Res>> listAll(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(announcementService.search(q, page, size));
    }

    /** Sửa nội dung thông báo (cả bản đã gửi tới người dùng). */
    @org.springframework.web.bind.annotation.PutMapping("/api/v1/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> update(@org.springframework.web.bind.annotation.PathVariable Long id,
                                      @Valid @RequestBody com.lms.announcement.dto.SystemAnnouncementDto.UpdateReq req) {
        return ResponseEntity.ok(announcementService.update(id, req));
    }

    /** Thu hồi thông báo: ẩn khỏi người dùng, giữ bản ghi. */
    @PostMapping("/api/v1/admin/announcements/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> revoke(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return ResponseEntity.ok(announcementService.revoke(id));
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
