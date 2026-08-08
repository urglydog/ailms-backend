package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.entity.LessonDocument;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.LessonDocumentRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.media.FfprobeService;
import com.lms.common.media.YoutubeMetadataService;
import com.lms.common.storage.StorageService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra toggle Preview (không giới hạn số lượng), BR-ROLE-01 (ownership), F4.1 nạp video. */
@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private LessonRepository lessonRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private LessonDocumentRepository lessonDocumentRepository;
    @Mock private StorageService storageService;
    @Mock private FfprobeService ffprobeService;
    @Mock private YoutubeMetadataService youtubeMetadataService;

    @InjectMocks
    private LessonService lessonService;

    private Lesson lesson;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail(OWNER_EMAIL);

        Course course = new Course();
        course.setId(100L);
        course.setInstructor(instructor);

        Chapter chapter = new Chapter();
        chapter.setId(20L);
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(30L);
        lesson.setTitle("Bài 1");
        lesson.setChapter(chapter);
        lesson.setIsPreview(false);
        lesson.setStatus("READY");

        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));
        lenient().when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_canEnablePreviewWithNoQuantityLimit() {
        Res result = lessonService.update(OWNER_EMAIL, 30L, new UpdateReq("Bài 1", true));

        assertThat(result.isPreview()).isTrue();
    }

    @Test
    void update_canDisablePreview() {
        lesson.setIsPreview(true);

        Res result = lessonService.update(OWNER_EMAIL, 30L, new UpdateReq("Bài 1", false));

        assertThat(result.isPreview()).isFalse();
    }

    @Test
    void update_throwsWhenCallerIsNotOwner() {
        assertThatThrownBy(() -> lessonService.update("khac@lms.local", 30L, new UpdateReq("Bài 1", false)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void uploadVideo_validMp4SetsReadyWithRealDuration() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", "fake-mp4-bytes".getBytes());
        when(ffprobeService.probeDurationSec(any(Path.class))).thenReturn(300);
        when(storageService.upload(anyString(), any(), anyLong(), anyString()))
                .thenReturn("https://cdn.example.com/videos/30/x.mp4");

        Res result = lessonService.uploadVideo(OWNER_EMAIL, 30L, file);

        assertThat(result.videoSource()).isEqualTo("UPLOAD");
        assertThat(result.videoUrl()).isEqualTo("https://cdn.example.com/videos/30/x.mp4");
        assertThat(result.durationSec()).isEqualTo(300);
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void uploadVideo_throwsWhenDurationBelowOneMinute() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", "fake-mp4-bytes".getBytes());
        when(ffprobeService.probeDurationSec(any(Path.class))).thenReturn(30);

        assertThatThrownBy(() -> lessonService.uploadVideo(OWNER_EMAIL, 30L, file))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void uploadVideo_throwsWhenDurationAboveOneHundredEightyMinutes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", "fake-mp4-bytes".getBytes());
        when(ffprobeService.probeDurationSec(any(Path.class))).thenReturn(180 * 60 + 1);

        assertThatThrownBy(() -> lessonService.uploadVideo(OWNER_EMAIL, 30L, file))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void uploadVideo_throwsWhenNotMp4() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> lessonService.uploadVideo(OWNER_EMAIL, 30L, file))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void setYoutubeVideo_validPublicUrlSetsReady() {
        SetYoutubeReq req = new SetYoutubeReq("https://www.youtube.com/watch?v=abc12345678");
        when(youtubeMetadataService.extractVideoId(req.url())).thenReturn("abc12345678");
        when(youtubeMetadataService.fetchDurationSec("abc12345678")).thenReturn(600);

        Res result = lessonService.setYoutubeVideo(OWNER_EMAIL, 30L, req);

        assertThat(result.videoSource()).isEqualTo("YOUTUBE");
        assertThat(result.youtubeId()).isEqualTo("abc12345678");
        assertThat(result.durationSec()).isEqualTo(600);
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void setYoutubeVideo_throwsWhenVideoNotPublic() {
        SetYoutubeReq req = new SetYoutubeReq("https://www.youtube.com/watch?v=abc12345678");
        when(youtubeMetadataService.extractVideoId(req.url())).thenReturn("abc12345678");
        when(youtubeMetadataService.fetchDurationSec("abc12345678"))
                .thenThrow(new InvalidRequestException("Video YouTube không tồn tại hoặc không công khai"));

        assertThatThrownBy(() -> lessonService.setYoutubeVideo(OWNER_EMAIL, 30L, req))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void uploadVideo_throwsWhenLessonAlreadyHasUploadVideo() {
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://cdn.example.com/videos/30/old.mp4");
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", "fake-mp4-bytes".getBytes());

        assertThatThrownBy(() -> lessonService.uploadVideo(OWNER_EMAIL, 30L, file))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(ffprobeService, never()).probeDurationSec(any());
    }

    @Test
    void setYoutubeVideo_throwsWhenLessonAlreadyHasUploadVideo() {
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://cdn.example.com/videos/30/old.mp4");
        SetYoutubeReq req = new SetYoutubeReq("https://www.youtube.com/watch?v=abc12345678");

        assertThatThrownBy(() -> lessonService.setYoutubeVideo(OWNER_EMAIL, 30L, req))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(youtubeMetadataService, never()).extractVideoId(anyString());
    }

    @Test
    void deleteVideo_removesUploadVideoAndDeletesFromStorage() {
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://bucket.b2.example.com/videos/30/old.mp4");
        lesson.setDurationSec(300);

        Res result = lessonService.deleteVideo(OWNER_EMAIL, 30L);

        assertThat(result.videoSource()).isNull();
        assertThat(result.videoUrl()).isNull();
        assertThat(result.durationSec()).isZero();
        assertThat(result.status()).isEqualTo("DRAFT");
        verify(storageService).delete("videos/30/old.mp4");
    }

    @Test
    void deleteVideo_clearsYoutubeVideoWithoutTouchingStorage() {
        lesson.setVideoSource("YOUTUBE");
        lesson.setVideoUrl("https://www.youtube.com/watch?v=abc12345678");
        lesson.setYoutubeId("abc12345678");

        Res result = lessonService.deleteVideo(OWNER_EMAIL, 30L);

        assertThat(result.videoSource()).isNull();
        assertThat(result.status()).isEqualTo("DRAFT");
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void deleteVideo_throwsWhenNoVideoExists() {
        assertThatThrownBy(() -> lessonService.deleteVideo(OWNER_EMAIL, 30L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void delete_cascadesVideoAndDocumentsToStorageBeforeRemovingLesson() {
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://bucket.b2.example.com/videos/30/video.mp4");
        LessonDocument doc1 = new LessonDocument();
        doc1.setFileUrl("https://bucket.b2.example.com/documents/30/a.pdf");
        LessonDocument doc2 = new LessonDocument();
        doc2.setFileUrl("https://bucket.b2.example.com/documents/30/b.pdf");
        when(lessonDocumentRepository.findByLesson_IdOrderByIdAsc(30L)).thenReturn(List.of(doc1, doc2));

        lessonService.delete(OWNER_EMAIL, 30L);

        verify(storageService).delete("documents/30/a.pdf");
        verify(storageService).delete("documents/30/b.pdf");
        verify(storageService).delete("videos/30/video.mp4");
        verify(lessonDocumentRepository).deleteAll(List.of(doc1, doc2));
        verify(lessonRepository).delete(lesson);
    }

    @Test
    void loadLessonForModeration_allowsWhenCourseIsPending() {
        lesson.getChapter().getCourse().setStatus(CourseStatus.PENDING);

        Lesson result = lessonService.loadLessonForModeration(30L);

        assertThat(result).isSameAs(lesson);
    }

    @Test
    void loadLessonForModeration_throwsWhenCourseIsNotPending() {
        lesson.getChapter().getCourse().setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> lessonService.loadLessonForModeration(30L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
