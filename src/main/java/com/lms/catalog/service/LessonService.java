package com.lms.catalog.service;

import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.media.FfprobeService;
import com.lms.common.media.YoutubeMetadataService;
import com.lms.common.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quản lý bài học — metadata (F2.1, UC33) + video (Giai đoạn 4, UC34).
 * Mọi thao tác ghi kiểm ownership qua {@code lesson.getChapter().getCourse().getInstructor()}.
 */
@Service
@RequiredArgsConstructor
public class LessonService {

    /** BR-CHUNK-01: thời lượng bài giảng cho phép 1–180 phút. */
    private static final int MIN_DURATION_SEC = 60;
    private static final int MAX_DURATION_SEC = 180 * 60;

    private final LessonRepository lessonRepository;
    private final ChapterRepository chapterRepository;
    private final StorageService storageService;
    private final FfprobeService ffprobeService;
    private final YoutubeMetadataService youtubeMetadataService;

    @Transactional
    public Res create(String instructorEmail, Long chapterId, CreateReq req) {
        Chapter chapter = loadOwnedChapter(chapterId, instructorEmail);

        Lesson lesson = new Lesson();
        lesson.setTitle(req.title());
        lesson.setChapter(chapter);
        lesson.setDisplayOrder((int) lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapterId).size());
        lesson.setStatus("DRAFT");
        lesson.setIsPreview(false);

        return mapToRes(lessonRepository.save(lesson));
    }

    @Transactional
    public Res update(String instructorEmail, Long lessonId, UpdateReq req) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);
        lesson.setTitle(req.title());
        lesson.setIsPreview(req.isPreview());
        return mapToRes(lessonRepository.save(lesson));
    }

    @Transactional
    public void delete(String instructorEmail, Long lessonId) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);
        lessonRepository.delete(lesson);
    }

    @Transactional
    public void reorder(String instructorEmail, Long chapterId, ReorderReq req) {
        loadOwnedChapter(chapterId, instructorEmail);
        List<Lesson> lessons = lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapterId);
        Map<Long, Lesson> byId = new HashMap<>();
        lessons.forEach(lesson -> byId.put(lesson.getId(), lesson));

        if (!byId.keySet().equals(new HashSet<>(req.orderedIds()))) {
            throw new InvalidRequestException("Danh sách thứ tự không khớp với các bài học hiện có");
        }

        for (int i = 0; i < req.orderedIds().size(); i++) {
            Lesson lesson = byId.get(req.orderedIds().get(i));
            lesson.setDisplayOrder(i);
            lessonRepository.save(lesson);
        }
    }

    /** F4.1 (UC34) — nạp MP4 trực tiếp lên B2, lấy durationSec thật bằng ffprobe. */
    @Transactional
    public Res uploadVideo(String instructorEmail, Long lessonId, MultipartFile file) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);

        if (file.isEmpty()) {
            throw new InvalidRequestException("File video trống");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean looksLikeMp4 = "video/mp4".equals(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".mp4"));
        if (!looksLikeMp4) {
            throw new InvalidRequestException("Chỉ chấp nhận file .mp4");
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("lesson-video-", ".mp4");
            file.transferTo(tempFile);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file video: " + e.getMessage());
        }

        try {
            int durationSec = ffprobeService.probeDurationSec(tempFile);
            if (durationSec < MIN_DURATION_SEC || durationSec > MAX_DURATION_SEC) {
                throw new BusinessRuleViolationException(
                        "Thời lượng video phải từ 1 đến 180 phút (hiện tại: " + (durationSec / 60) + " phút)");
            }

            String key = "videos/" + lessonId + "/" + UUID.randomUUID() + ".mp4";
            String url;
            try (InputStream in = Files.newInputStream(tempFile)) {
                url = storageService.upload(key, in, Files.size(tempFile), "video/mp4");
            }

            lesson.setVideoSource("UPLOAD");
            lesson.setVideoUrl(url);
            lesson.setYoutubeId(null);
            lesson.setDurationSec(durationSec);
            lesson.setStatus("READY");
            return mapToRes(lessonRepository.save(lesson));
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được video lên kho lưu trữ: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // File tạm sẽ được hệ điều hành dọn định kỳ nếu xoá thất bại — không chặn request.
            }
        }
    }

    /** F4.1 (UC34) — dán link YouTube công khai thay cho upload MP4. */
    @Transactional
    public Res setYoutubeVideo(String instructorEmail, Long lessonId, SetYoutubeReq req) {
        Lesson lesson = loadOwnedLesson(lessonId, instructorEmail);

        String videoId = youtubeMetadataService.extractVideoId(req.url());
        int durationSec = youtubeMetadataService.fetchDurationSec(videoId);
        if (durationSec < MIN_DURATION_SEC || durationSec > MAX_DURATION_SEC) {
            throw new BusinessRuleViolationException(
                    "Thời lượng video phải từ 1 đến 180 phút (hiện tại: " + (durationSec / 60) + " phút)");
        }

        lesson.setVideoSource("YOUTUBE");
        lesson.setVideoUrl(req.url());
        lesson.setYoutubeId(videoId);
        lesson.setDurationSec(durationSec);
        lesson.setStatus("READY");
        return mapToRes(lessonRepository.save(lesson));
    }

    private Chapter loadOwnedChapter(Long chapterId, String instructorEmail) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
        if (!chapter.getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên chương này");
        }
        return chapter;
    }

    /** Package-private: cũng dùng bởi {@link LessonDocumentService} để tránh nhân đôi kiểm ownership. */
    Lesson loadOwnedLesson(Long lessonId, String instructorEmail) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (!lesson.getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên bài học này");
        }
        return lesson;
    }

    private Res mapToRes(Lesson lesson) {
        return new Res(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getDisplayOrder(),
                lesson.getIsPreview(),
                lesson.getStatus(),
                lesson.getVideoSource(),
                lesson.getVideoUrl(),
                lesson.getYoutubeId(),
                lesson.getDurationSec()
        );
    }
}
