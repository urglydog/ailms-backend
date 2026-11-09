package com.lms.live.dto;

import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import java.time.LocalDateTime;

public class LiveViewDto {

    /** UC51 — 1 dòng trong danh sách phiên LIVE/SCHEDULED của 1 khóa học (đã lọc theo BR-LIVE-01). */
    public record SummaryRes(
            Long id,
            String title,
            LiveSessionStatus status,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt
    ) {}

    /**
     * F11.9 — 1 dòng trong trang khám phá `/live` (cả 2 tab Công khai/Khóa học của tôi dùng chung
     * shape này). Khác {@link SummaryRes} (scoped sẵn theo 1 khóa học, không cần lặp lại tên khóa) —
     * đây là danh sách GỘP nhiều khóa, nên bắt buộc có {@code courseId}/{@code courseTitle} để FE
     * hiện + link sang đúng khóa học, và {@code instructorName}/{@code sourceLanguage} để đủ 5 mục
     * đã chốt trên thẻ.
     */
    public record FeedItemRes(
            Long id,
            String title,
            /** F11.9 mở rộng — ĐÃ áp dụng fallback (`session.thumbnailUrl` nếu có, không thì
             * `course.thumbnailUrl`) ngay ở tầng service — FE dùng thẳng, không cần tự quyết định
             * fallback nào cả. Vẫn có thể `null` nếu khóa học cũng chưa có ảnh bìa nào. */
            String thumbnailUrl,
            LiveSessionStatus status,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt,
            Long courseId,
            String courseTitle,
            /** FE điều hướng sang trang chi tiết khóa học qua slug (`/courses/{slug}`), không phải
             * {@code courseId} — khớp route `app/(public)/courses/[slug]/page.tsx` đã có sẵn. */
            String courseSlug,
            String instructorName,
            String sourceLanguage
    ) {}

    /**
     * UC51 — chi tiết 1 phiên cho người XEM. {@code viewerToken}/{@code serverUrl}/{@code roomName}
     * chỉ có giá trị khi {@code status = LIVE} (chưa/không còn gì để connect khi SCHEDULED/ENDED).
     */
    public record DetailRes(
            Long id,
            String title,
            LiveVisibility visibility,
            LiveSessionStatus status,
            String sourceLanguage,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Long courseId,
            String courseTitle,
            String viewerToken,
            String serverUrl,
            String roomName
    ) {}
}
