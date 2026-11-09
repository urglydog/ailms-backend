package com.lms.dubbing.service;

import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.service.NotificationService;
import com.lms.dubbing.dto.InternalDubbingDto.JobContextRes;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.entity.Transcript;
import com.lms.dubbing.entity.TranscriptSegment;
import com.lms.dubbing.entity.VoiceMapping;
import com.lms.dubbing.repository.AiJobChunkRepository;
import com.lms.dubbing.repository.AiJobRepository;
import com.lms.dubbing.repository.AudioChunkRepository;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.dubbing.repository.TranscriptRepository;
import com.lms.dubbing.repository.TranscriptSegmentRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * UC20 — {@code getContext()} quyết định AI Worker có được BỎ QUA bước ASR hay không
 * (BR-DUB-01). Bộ test này bảo vệ đúng phát hiện thực tế: một job TRƯỚC ĐÓ bị huỷ/thất bại
 * giữa chừng có thể để lại Transcript gốc CHỈ PHỦ MỘT PHẦN video — nếu vẫn coi là "đã có" thì
 * job mới sẽ bỏ qua ASR cho CẢ video, khiến các đoạn sau (không khớp câu thoại nào đã lưu) bị
 * hiểu nhầm là khoảng lặng và giữ nguyên audio GỐC dù job vẫn báo COMPLETED.
 */
@ExtendWith(MockitoExtension.class)
class InternalDubbingServiceTest {

    private static final Long JOB_ID = 1L;
    private static final Long LESSON_ID = 30L;

    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJobChunkRepository aiJobChunkRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private TranscriptRepository transcriptRepository;
    @Mock private TranscriptSegmentRepository transcriptSegmentRepository;
    @Mock private AudioTrackRepository audioTrackRepository;
    @Mock private AudioChunkRepository audioChunkRepository;
    @Mock private VoiceMappingRepository voiceMappingRepository;
    @Mock private DubbingLockService dubbingLockService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private InternalDubbingService internalDubbingService;

    private Lesson lesson;
    private AiJob job;

    @BeforeEach
    void setUp() {
        lesson = new Lesson();
        lesson.setId(LESSON_ID);
        lesson.setVideoSource("UPLOAD");
        lesson.setVideoUrl("https://cdn.example.com/videos/30/x.mp4");
        lesson.setSourceLanguage("en-US");
        lesson.setDurationSec(1200); // video 20 phut

        job = new AiJob();
        job.setId(JOB_ID);
        job.setLesson(lesson);
        job.setTargetLanguage("vi-VN");

        when(aiJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        VoiceMapping voice = new VoiceMapping();
        voice.setLanguage("vi-VN");
        voice.setVoiceName("vi-VN-HoaiMyNeural");
        voice.setIsDefault(true);
        lenient().when(voiceMappingRepository.findByLanguageAndIsActiveTrue("vi-VN")).thenReturn(List.of(voice));
    }

    private TranscriptSegment segment(int seq, double startSec, double endSec) {
        TranscriptSegment s = new TranscriptSegment();
        s.setSeq(seq);
        s.setStartSec(BigDecimal.valueOf(startSec));
        s.setEndSec(BigDecimal.valueOf(endSec));
        s.setText("cau " + seq);
        return s;
    }

    @Test
    void getContext_jobHasChosenVoice_usesItInsteadOfDefault() {
        VoiceMapping chosen = new VoiceMapping();
        chosen.setLanguage("vi-VN");
        chosen.setVoiceName("vi-VN-NamMinhNeural");
        job.setVoiceMapping(chosen);
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(LESSON_ID)).thenReturn(Optional.empty());

        JobContextRes ctx = internalDubbingService.getContext(JOB_ID);

        assertThat(ctx.voiceName()).isEqualTo("vi-VN-NamMinhNeural");
    }

    @Test
    void getContext_jobHasNoChosenVoice_fallsBackToDefault() {
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(LESSON_ID)).thenReturn(Optional.empty());

        JobContextRes ctx = internalDubbingService.getContext(JOB_ID);

        assertThat(ctx.voiceName()).isEqualTo("vi-VN-HoaiMyNeural"); // giong isDefault stub o setUp()
    }

    @Test
    void getContext_noSourceTranscript_reportsUnavailable() {
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(LESSON_ID)).thenReturn(Optional.empty());

        JobContextRes ctx = internalDubbingService.getContext(JOB_ID);

        assertThat(ctx.sourceTranscriptAvailable()).isFalse();
        assertThat(ctx.sourceSegments()).isEmpty();
    }

    @Test
    void getContext_transcriptCoveringFullDuration_reportsAvailable() {
        Transcript transcript = new Transcript();
        transcript.setId(10L);
        transcript.setIsSource(true);
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(LESSON_ID)).thenReturn(Optional.of(transcript));
        // Cau cuoi ket thuc luc 1195s, video dai 1200s -> trong nguong dung sai 60s -> coi la DAY DU.
        when(transcriptSegmentRepository.findByTranscript_IdOrderBySeqAsc(10L))
                .thenReturn(List.of(segment(1, 0, 5), segment(2, 1190, 1195)));

        JobContextRes ctx = internalDubbingService.getContext(JOB_ID);

        assertThat(ctx.sourceTranscriptAvailable()).isTrue();
        assertThat(ctx.sourceSegments()).hasSize(2);
    }

    @Test
    void getContext_transcriptFromCancelledJobOnlyCoveringFirstChunk_reportsUnavailable() {
        // Mo phong dung tinh huong: job truoc bi huy sau khi xong DUNG 1 chunk 10 phut dau
        // (BR-CHUNK-02) cho video 20 phut -> Transcript goc chi co cau thoai toi giay 600.
        Transcript transcript = new Transcript();
        transcript.setId(11L);
        transcript.setIsSource(true);
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(LESSON_ID)).thenReturn(Optional.of(transcript));
        when(transcriptSegmentRepository.findByTranscript_IdOrderBySeqAsc(11L))
                .thenReturn(List.of(segment(1, 0, 5), segment(2, 590, 600)));

        JobContextRes ctx = internalDubbingService.getContext(JOB_ID);

        // PHAI coi nhu CHUA CO -> AI Worker chay lai ASR tu dau cho ca video, khong bo sot
        // chunk 2 (nguoc lai se bi hieu nham la khoang lang va giu nguyen audio goc).
        assertThat(ctx.sourceTranscriptAvailable()).isFalse();
        assertThat(ctx.sourceSegments()).isEmpty();
    }
}
