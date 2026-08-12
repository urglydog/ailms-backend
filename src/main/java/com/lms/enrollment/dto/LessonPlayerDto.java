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
            /**
             * Học viên có thật sự sở hữu khóa học không (khác {@link #isPreview}, vốn chỉ mô tả
             * BẢN THÂN bài học có phải bài học thử không) — FE dùng field này để quyết định mở
             * khoá Ghi chú/Học liệu AI/Socratic Tutor, KHÔNG dùng {@code isPreview} (một bài
             * preview vẫn có thể được xem bởi học viên đã sở hữu đầy đủ khóa học).
             */
            Boolean enrolled,
            /** Khôi phục vị trí xem dở (BR-PROGRESS-03) — 0 nếu chưa từng xem. */
            Integer lastPositionSec,
            List<LanguageRes> languages,
            /** UC21/22 — điều hướng + tiến độ từng bài trong khoá (sidebar "Trong khoá học này"). */
            List<ChapterNavRes> chapters
    ) {}

    public record ChapterNavRes(
            Long chapterId,
            String chapterTitle,
            Integer displayOrder,
            List<LessonNavRes> lessons
    ) {}

    public record LessonNavRes(
            Long lessonId,
            String lessonTitle,
            Integer displayOrder,
            Integer durationSec,
            Boolean isPreview,
            /** true nếu học viên đã xem đạt ngưỡng hoàn thành (BR-PROGRESS-01) — false nếu chưa từng xem. */
            Boolean isCompleted
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
