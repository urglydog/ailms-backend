package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.TrackStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.dubbing.entity.AudioChunk;
import com.lms.dubbing.entity.AudioTrack;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AudioChunkRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import com.lms.enrollment.dto.LessonPlayerDto.Res;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** UC16/17 — chặn chưa ghi danh, trả đúng danh sách ngôn ngữ active/inactive, chunks chỉ khi PARTIAL. */
@ExtendWith(MockitoExtension.class)
class LessonPlayerServiceTest {

    private static final String EMAIL = "student1@lms.local";

    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentSecurity enrollmentSecurity;
    @Mock private VoiceMappingRepository voiceMappingRepository;
    @Mock private AudioTrackRepository audioTrackRepository;
    @Mock private AudioChunkRepository audioChunkRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;

    private LessonPlayerService service;

    private Lesson lesson;
    private User user;

    @BeforeEach
    void setUp() {
        service = new LessonPlayerService(
                lessonRepository, userRepository, enrollmentSecurity,
                voiceMappingRepository, audioTrackRepository, audioChunkRepository, lessonProgressRepository);

        Course course = new Course();
        course.setId(10L);
        course.setTitle("Khoa hoc test");
        course.setSlug("khoa-hoc-test");

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(21L);
        lesson.setTitle("Bai 1");
        lesson.setChapter(chapter);
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://b2.example/video.mp4");
        lesson.setDurationSec(900);
        lesson.setSourceLanguage("en");
        lesson.setIsPreview(false);

        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        lenient().when(lessonRepository.findById(21L)).thenReturn(Optional.of(lesson));
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 21L)).thenReturn(Optional.empty());
        lenient().when(voiceMappingRepository.findByIsActiveTrue()).thenReturn(List.of());
    }

    @Test
    void chuaGhiDanh_khongPhaiPreview_biChan() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(false);

        assertThatThrownBy(() -> service.getLessonForPlayback(EMAIL, 21L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void daGhiDanh_traVeThongTinCoBan_vaLastPositionSec() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(true);
        LessonProgress progress = new LessonProgress();
        progress.setLastPositionSec(120);
        when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 21L)).thenReturn(Optional.of(progress));

        Res res = service.getLessonForPlayback(EMAIL, 21L);

        assertThat(res.lessonId()).isEqualTo(21L);
        assertThat(res.courseId()).isEqualTo(10L);
        assertThat(res.lastPositionSec()).isEqualTo(120);
        assertThat(res.languages()).isEmpty();
    }

    @Test
    void ngonNguChuaCoTrack_availableFalse_trackNull() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(true);
        VoiceMapping vi = voiceMapping("vi");
        when(voiceMappingRepository.findByIsActiveTrue()).thenReturn(List.of(vi));
        when(audioTrackRepository.findByLesson_IdAndLanguage(21L, "vi")).thenReturn(Optional.empty());

        Res res = service.getLessonForPlayback(EMAIL, 21L);

        assertThat(res.languages()).hasSize(1);
        assertThat(res.languages().get(0).code()).isEqualTo("vi");
        assertThat(res.languages().get(0).available()).isFalse();
        assertThat(res.languages().get(0).track()).isNull();
    }

    @Test
    void trackCompletedVoiFinalUrl_availableTrue_khongCoChunks() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(true);
        VoiceMapping vi = voiceMapping("vi");
        when(voiceMappingRepository.findByIsActiveTrue()).thenReturn(List.of(vi));

        AudioTrack track = new AudioTrack();
        track.setId(5L);
        track.setLanguage("vi");
        track.setStatus(TrackStatus.COMPLETED);
        track.setFinalUrl("https://b2.example/final.mp3");
        track.setDurationSec(900);
        when(audioTrackRepository.findByLesson_IdAndLanguage(21L, "vi")).thenReturn(Optional.of(track));

        Res res = service.getLessonForPlayback(EMAIL, 21L);

        var lang = res.languages().get(0);
        assertThat(lang.available()).isTrue();
        assertThat(lang.track().finalUrl()).isEqualTo("https://b2.example/final.mp3");
        assertThat(lang.track().chunks()).isEmpty();
    }

    @Test
    void trackPartial_availableTrue_traVeDanhSachChunkTheoThuTu() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(true);
        VoiceMapping vi = voiceMapping("vi");
        when(voiceMappingRepository.findByIsActiveTrue()).thenReturn(List.of(vi));

        AudioTrack track = new AudioTrack();
        track.setId(5L);
        track.setLanguage("vi");
        track.setStatus(TrackStatus.PARTIAL);
        track.setFinalUrl(null);
        when(audioTrackRepository.findByLesson_IdAndLanguage(21L, "vi")).thenReturn(Optional.of(track));

        AudioChunk chunk0 = chunk(0, 0, 600, "https://b2.example/chunk_0.mp3");
        when(audioChunkRepository.findByAudioTrack_IdOrderByChunkIndexAsc(5L)).thenReturn(List.of(chunk0));

        Res res = service.getLessonForPlayback(EMAIL, 21L);

        var lang = res.languages().get(0);
        assertThat(lang.available()).isTrue();
        assertThat(lang.track().status()).isEqualTo("PARTIAL");
        assertThat(lang.track().chunks()).hasSize(1);
        assertThat(lang.track().chunks().get(0).chunkIndex()).isEqualTo(0);
    }

    @Test
    void trackProcessing_availableFalse() {
        when(enrollmentSecurity.canAccessLesson(EMAIL, 21L, true)).thenReturn(true);
        VoiceMapping vi = voiceMapping("vi");
        when(voiceMappingRepository.findByIsActiveTrue()).thenReturn(List.of(vi));

        AudioTrack track = new AudioTrack();
        track.setId(5L);
        track.setLanguage("vi");
        track.setStatus(TrackStatus.PROCESSING);
        when(audioTrackRepository.findByLesson_IdAndLanguage(21L, "vi")).thenReturn(Optional.of(track));

        Res res = service.getLessonForPlayback(EMAIL, 21L);

        assertThat(res.languages().get(0).available()).isFalse();
    }

    private VoiceMapping voiceMapping(String language) {
        VoiceMapping vm = new VoiceMapping();
        vm.setLanguage(language);
        vm.setVoiceName(language + "-Voice");
        vm.setGender("FEMALE");
        vm.setIsActive(true);
        return vm;
    }

    private AudioChunk chunk(int index, int start, int end, String url) {
        AudioChunk c = new AudioChunk();
        c.setChunkIndex(index);
        c.setStartSec(start);
        c.setEndSec(end);
        c.setFileUrl(url);
        return c;
    }
}
