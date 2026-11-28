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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        // Hiệu năng (28/11/2026): trước đây mỗi ngôn ngữ đang bật = 2 truy vấn (track + phụ đề),
        // ~70 ngôn ngữ → ~150 truy vấn/lần mở player. Nay nạp 1 lần rồi tra theo mã ngôn ngữ.
        Map<String, AudioTrack> tracksByLang = new HashMap<>();
        for (AudioTrack t : audioTrackRepository.findByLesson_Id(lessonId)) {
            tracksByLang.putIfAbsent(t.getLanguage(), t);
        }
        Map<String, Transcript> transcriptsByLang = new HashMap<>();
        for (Transcript t : transcriptRepository.findByLesson_Id(lessonId)) {
            transcriptsByLang.putIfAbsent(t.getLanguage(), t);
        }
        List<LanguageRes> languages = voiceMappingRepository.findByIsActiveTrue().stream()
                .map(VoiceMapping::getLanguage)
                .distinct()
                .map(code -> toLanguageRes(code, tracksByLang.get(code), transcriptsByLang.get(code)))
                .toList();

        // Sidebar: 1 truy vấn bài của cả khoá + 1 truy vấn bài đã hoàn thành (trước: 2 truy vấn/bài).
        Map<Long, List<Lesson>> lessonsByChapter = new HashMap<>();
        List<Long> courseLessonIds = new ArrayList<>();
        for (Lesson l : lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(course.getId())) {
            lessonsByChapter.computeIfAbsent(l.getChapter().getId(), k -> new ArrayList<>()).add(l);
            courseLessonIds.add(l.getId());
        }
        Set<Long> completedIds = courseLessonIds.isEmpty() ? Set.of()
                : lessonProgressRepository.findCompletedLessonIdsByUserIdAndLessonIdIn(userId, courseLessonIds);
        List<ChapterNavRes> chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAsc(course.getId()).stream()
                .map(chapter -> toChapterNavRes(chapter, lessonsByChapter.getOrDefault(chapter.getId(), List.of()), completedIds))
                .toList();

        // BR-ENROLL-02/03: sở hữu khóa học là điều kiện mở khoá Ghi chú/Học liệu AI/Socratic
        // Tutor — KHÔNG phải `lesson.isPreview` (một bài preview vẫn xem được bởi học viên đã
        // sở hữu đầy đủ khóa học, không có nghĩa nội dung nâng cao bị khoá với họ).
        //
        // (19/09/2026) — Giảng viên sở hữu khóa cũng coi như "đã ghi danh" ở đây, để nút "Xem
        // trước > Với tư cách là Giảng viên" (trang chỉnh sửa khóa học) thấy ĐẦY ĐỦ trải nghiệm
        // (Hỏi đáp/Học liệu/Bài tập/Gia sư AI), không bị khóa như khách chưa mua.
        boolean owner = course.getInstructor() != null && course.getInstructor().getEmail().equals(email);
        boolean enrolled = enrollmentRepository.existsByUser_EmailAndCourse_Id(email, course.getId()) || owner;

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
                loadSubtitles(transcriptRepository.findByLesson_IdAndIsSourceTrue(lessonId)),
                owner
        );
    }

    /**
     * Phụ đề GỐC có ngay sau khi giảng viên nạp video — {@code TranscriptExtractionService}
     * (UC34 mở rộng) tự chạy ASR nền ngay lúc upload, không cần chờ ai bấm "Lồng tiếng AI" nữa.
     * Trước khi job nền đó chạy xong (vài chục giây tới vài phút tuỳ độ dài video), hoặc nếu job
     * lỗi, hàm này vẫn AN TOÀN — trả danh sách rỗng, KHÔNG lỗi, học viên chỉ đơn giản là chưa
     * thấy nút "Gốc" khả dụng, thử lại sau khi tải lại trang.
     *
     * <p>Phụ đề DỊCH (ngôn ngữ khác) thì vẫn phụ thuộc đã có bản dịch cho đúng ngôn ngữ đó chưa
     * (do lồng tiếng HOẶC sinh học liệu tạo ra — xem {@code TranscriptPersistenceService}).
     */
    private List<SubtitleSegmentRes> loadSubtitles(Optional<Transcript> transcriptOpt) {
        return transcriptOpt
                .map(t -> transcriptSegmentRepository.findByTranscript_IdOrderBySeqAsc(t.getId()).stream()
                        .map(s -> new SubtitleSegmentRes(s.getSeq(), s.getStartSec(), s.getEndSec(), s.getText()))
                        .toList())
                .orElse(List.of());
    }

    /** UC21/22 — sidebar "Trong khoá học này": điều hướng + đánh dấu bài đã hoàn thành. */
    private ChapterNavRes toChapterNavRes(Chapter chapter, List<Lesson> chapterLessons, Set<Long> completedIds) {
        List<LessonNavRes> lessons = chapterLessons.stream()
                .map(lesson -> toLessonNavRes(lesson, completedIds.contains(lesson.getId())))
                .toList();
        return new ChapterNavRes(chapter.getId(), chapter.getTitle(), chapter.getDisplayOrder(), lessons);
    }

    private LessonNavRes toLessonNavRes(Lesson lesson, boolean isCompleted) {
        return new LessonNavRes(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getDisplayOrder(),
                lesson.getDurationSec(),
                lesson.getIsPreview(),
                isCompleted
        );
    }

    private LanguageRes toLanguageRes(String languageCode, AudioTrack track, Transcript transcript) {
        Optional<AudioTrack> trackOpt = Optional.ofNullable(track);
        boolean available = trackOpt.filter(this::isPlayable).isPresent();
        AudioTrackRes trackRes = trackOpt.map(this::toAudioTrackRes).orElse(null);
        List<SubtitleSegmentRes> subtitles = loadSubtitles(Optional.ofNullable(transcript));
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
