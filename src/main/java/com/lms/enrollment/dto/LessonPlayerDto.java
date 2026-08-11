package com.lms.enrollment.dto;

import java.util.List;

/** UC16/17 — dữ liệu phát bài học cho học viên ĐÃ đăng nhập (khác UC11 preview ẩn danh). */
public class LessonPlayerDto {

    public record Res(
            Long lessonId,
            String lessonTitle,
            Long courseId,
            String courseTitle,
            String courseSlug,
            String videoSource,
            String videoUrl,
            String youtubeId,
            Integer durationSec,
            String sourceLanguage,
            Boolean isPreview,
            /** Khôi phục vị trí xem dở (BR-PROGRESS-03) — 0 nếu chưa từng xem. */
            Integer lastPositionSec,
            List<LanguageRes> languages
    ) {}

    /** Một ngôn ngữ lồng tiếng đang active (BR-DUB-07) kèm track nếu đã có. */
    public record LanguageRes(
            String code,
            String label,
            boolean available,
            AudioTrackRes track
    ) {}

    public record AudioTrackRes(
            Long id,
            String language,
            String status,
            /** Có giá trị khi đã ghép xong; null thì phát theo {@link #chunks} (BR-CHUNK-05). */
            String finalUrl,
            Integer durationSec,
            List<AudioChunkRes> chunks
    ) {}

    public record AudioChunkRes(
            Integer chunkIndex,
            Integer startSec,
            Integer endSec,
            String fileUrl
    ) {}
}
