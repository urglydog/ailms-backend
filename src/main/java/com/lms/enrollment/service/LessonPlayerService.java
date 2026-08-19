package com.lms.enrollment.service;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.entity.AudioChunk;
import com.lms.dubbing.entity.AudioTrack;
import com.lms.dubbing.entity.Transcript;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AudioChunkRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.TranscriptRepository;
import com.lms.dubbing.repository.TranscriptSegmentRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.enrollment.dto.LessonPlayerDto.AudioChunkRes;
import com.lms.enrollment.dto.LessonPlayerDto.AudioTrackRes;
import com.lms.enrollment.dto.LessonPlayerDto.ChapterNavRes;
import com.lms.enrollment.dto.LessonPlayerDto.LanguageRes;
import com.lms.enrollment.dto.LessonPlayerDto.LessonNavRes;
import com.lms.enrollment.dto.LessonPlayerDto.Res;
import com.lms.enrollment.dto.LessonPlayerDto.SubtitleSegmentRes;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC16/17 — phát bài học cho học viên ĐÃ đăng nhập (đã ghi danh, hoặc bài Preview). Khác
 * {@code CoursePublicService.getLessonForPlayback} (UC11): endpoint đó phục vụ khách ẩn danh,
 * chỉ mở bài Preview, không có tiến độ/danh sách ngôn ngữ lồng tiếng.
 */
@Service
@RequiredArgsConstructor
public class LessonPlayerService {

    private final LessonRepository lessonRepository;
    private final ChapterRepository chapterRepository;
    private final com.lms.auth.repository.UserRepository userRepository;
    private final EnrollmentSecurity enrollmentSecurity;
    private final VoiceMappingRepository voiceMappingRepository;
    private final AudioTrackRepository audioTrackRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

    @Transactional(readOnly = true)
    public Res getLessonForPlayback(String email, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        if (!enrollmentSecurity.canAccessLesson(email, lessonId, true)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }

        Course course = lesson.getChapter().getCourse();
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email))
                .getId();

        Integer lastPositionSec = lessonProgressRepository.findByUser_IdAndLesson_Id(userId, lessonId)
                .map(p -> p.getLastPositionSec())
                .orElse(0);

        List<LanguageRes> languages = voiceMappingRepository.findByIsActiveTrue().stream()
                .map(VoiceMapping::getLanguage)
                .distinct()
                .map(code -> toLanguageRes(code, lessonId))
                .toList();

        List<ChapterNavRes> chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAsc(course.getId()).stream()
                .map(chapter -> toChapterNavRes(chapter, userId))
                .toList();

        // BR-ENROLL-02/03: sở hữu khóa học là điều kiện mở khoá Ghi chú/Học liệu AI/Socratic
        // Tutor — KHÔNG phải `lesson.isPreview` (một bài preview vẫn xem được bởi học viên đã
        // sở hữu đầy đủ khóa học, không có nghĩa nội dung nâng cao bị khoá với họ).
        boolean enrolled = enrollmentRepository.existsByUser_EmailAndCourse_Id(email, course.getId());

        return new Res(
                lesson.getId(),
                lesson.getTitle(),
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                lesson.getVideoSource(),
                lesson.getVideoUrl(),
                lesson.getYoutubeId(),
                lesson.getDurationSec(),
                lesson.getSourceLanguage(),
                lesson.getIsPreview(),
                enrolled,
                lastPositionSec,
                languages,
                chapters,
                loadSubtitles(transcriptRepository.findByLesson_IdAndIsSourceTrue(lessonId))
        );
    }

    /**
     * Phụ đề gốc/dịch không có gì để hiện khi bài học chưa từng qua pipeline lồng tiếng
     * (Transcript gốc chỉ tạo ở lần dịch đầu tiên) hoặc ngôn ngữ đó chưa lồng tiếng xong —
     * cả 2 trường hợp trả danh sách rỗng, KHÔNG lỗi.
     */
    private List<SubtitleSegmentRes> loadSubtitles(Optional<Transcript> transcriptOpt) {
        return transcriptOpt
                .map(t -> transcriptSegmentRepository.findByTranscript_IdOrderBySeqAsc(t.getId()).stream()
                        .map(s -> new SubtitleSegmentRes(s.getSeq(), s.getStartSec(), s.getEndSec(), s.getText()))
                        .toList())
                .orElse(List.of());
    }

    /** UC21/22 — sidebar "Trong khoá học này": điều hướng + đánh dấu bài đã hoàn thành. */
    private ChapterNavRes toChapterNavRes(Chapter chapter, Long userId) {
        List<LessonNavRes> lessons = lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId()).stream()
                .map(lesson -> toLessonNavRes(lesson, userId))
                .toList();
        return new ChapterNavRes(chapter.getId(), chapter.getTitle(), chapter.getDisplayOrder(), lessons);
    }

    private LessonNavRes toLessonNavRes(Lesson lesson, Long userId) {
        boolean isCompleted = lessonProgressRepository.findByUser_IdAndLesson_Id(userId, lesson.getId())
                .map(p -> p.getIsCompleted())
                .orElse(false);
        return new LessonNavRes(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getDisplayOrder(),
                lesson.getDurationSec(),
                lesson.getIsPreview(),
                isCompleted
        );
    }

    private LanguageRes toLanguageRes(String languageCode, Long lessonId) {
        Optional<AudioTrack> trackOpt = audioTrackRepository.findByLesson_IdAndLanguage(lessonId, languageCode);
        boolean available = trackOpt.filter(this::isPlayable).isPresent();
        AudioTrackRes trackRes = trackOpt.map(this::toAudioTrackRes).orElse(null);
        List<SubtitleSegmentRes> subtitles =
                loadSubtitles(transcriptRepository.findByLesson_IdAndLanguage(lessonId, languageCode));
        return new LanguageRes(languageCode, displayLabel(languageCode), available, trackRes, subtitles);
    }

    /** Học viên chỉ chọn được ngôn ngữ đã có ít nhất 1 phần nghe được (BR-CHUNK-05). */
    private boolean isPlayable(AudioTrack track) {
        return switch (track.getStatus()) {
            case COMPLETED, PARTIAL -> true;
            default -> false;
        };
    }

    private AudioTrackRes toAudioTrackRes(AudioTrack track) {
        List<AudioChunkRes> chunks = track.getFinalUrl() != null
                ? List.of()
                : audioChunkRepository.findByAudioTrack_IdOrderByChunkIndexAsc(track.getId()).stream()
                        .map(this::toAudioChunkRes)
                        .toList();
        return new AudioTrackRes(
                track.getId(),
                track.getLanguage(),
                track.getStatus().name(),
                track.getFinalUrl(),
                track.getDurationSec(),
                chunks
        );
    }

    private AudioChunkRes toAudioChunkRes(AudioChunk chunk) {
        return new AudioChunkRes(chunk.getChunkIndex(), chunk.getStartSec(), chunk.getEndSec(), chunk.getFileUrl());
    }

    /** Nhãn hiển thị sinh từ mã ngôn ngữ — KHÔNG hardcode danh sách (BR-DUB-07). */
    private String displayLabel(String languageCode) {
        return Locale.forLanguageTag(languageCode).getDisplayName(Locale.forLanguageTag("vi-VN"));
    }
}
