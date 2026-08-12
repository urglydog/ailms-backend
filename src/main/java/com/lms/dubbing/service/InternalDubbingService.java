package com.lms.dubbing.service;

import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.auth.entity.User;
import com.lms.common.enums.JobStatus;
import com.lms.common.enums.TrackStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.dubbing.dto.InternalDubbingDto.ChunkUpsertReq;
import com.lms.dubbing.dto.InternalDubbingDto.FinishReq;
import com.lms.dubbing.dto.InternalDubbingDto.JobContextRes;
import com.lms.dubbing.dto.InternalDubbingDto.SegmentDto;
import com.lms.dubbing.dto.InternalDubbingDto.StartReq;
import com.lms.dubbing.entity.AiJob;
import com.lms.dubbing.entity.AiJobChunk;
import com.lms.dubbing.entity.AudioChunk;
import com.lms.dubbing.entity.AudioTrack;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC19 — kênh DUY NHẤT để AI Worker (Python, không kết nối MySQL trực tiếp) ghi kết quả
 * pipeline lồng tiếng vào CSDL. Gọi qua {@code /api/internal/dubbing/**}, xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter} chứ không phải JWT.
 */
@Service
@RequiredArgsConstructor
public class InternalDubbingService {

    private final AiJobRepository aiJobRepository;
    private final AiJobChunkRepository aiJobChunkRepository;
    private final LessonRepository lessonRepository;
    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final AudioTrackRepository audioTrackRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final VoiceMappingRepository voiceMappingRepository;
    private final DubbingLockService dubbingLockService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public JobContextRes getContext(Long jobId) {
        AiJob job = loadJob(jobId);
        Lesson lesson = job.getLesson();
        String voiceName = resolveVoiceName(job.getTargetLanguage());

        Optional<Transcript> sourceTranscript = transcriptRepository.findByLesson_IdAndIsSourceTrue(lesson.getId());
        List<SegmentDto> sourceSegments = sourceTranscript
                .map(t -> transcriptSegmentRepository.findByTranscript_IdOrderBySeqAsc(t.getId()))
                .orElse(List.of())
                .stream()
                .map(this::toSegmentDto)
                .toList();

        return new JobContextRes(
                job.getId(),
                lesson.getId(),
                lesson.getVideoSource(),
                lesson.getVideoUrl(),
                lesson.getDurationSec(),
                lesson.getSourceLanguage(),
                job.getTargetLanguage(),
                voiceName,
                sourceTranscript.isPresent(),
                sourceSegments
        );
    }

    @Transactional
    public void start(Long jobId, StartReq req) {
        AiJob job = loadJob(jobId);
        job.setStatus(JobStatus.PROCESSING);
        job.setTotalChunks(req.totalChunks());
        job.setCeleryTaskId(req.celeryTaskId());
        job.setStartedAt(LocalDateTime.now());
        aiJobRepository.save(job);
    }

    @Transactional
    public void upsertChunk(Long jobId, Integer chunkIndex, ChunkUpsertReq req) {
        AiJob job = loadJob(jobId);
        JobStatus status = parseChunkStatus(req.status());

        AiJobChunk chunk = aiJobChunkRepository.findByAiJob_IdAndChunkIndex(jobId, chunkIndex).orElseGet(AiJobChunk::new);
        boolean wasTerminal = !chunk.isNew() && isTerminal(chunk.getStatus());
        chunk.setAiJob(job);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartSec(req.startSec());
        chunk.setEndSec(req.endSec());
        chunk.setStatus(status);
        if (status == JobStatus.FAILED) {
            chunk.setRetryCount(chunk.getRetryCount() + 1);
            job.setErrorMessage("Chunk " + chunkIndex + ": " + req.errorMessage());
        }
        aiJobChunkRepository.save(chunk);

        if (!wasTerminal && isTerminal(status)) {
            job.setDoneChunks(job.getDoneChunks() + 1);
        }
        aiJobRepository.save(job);

        if (status != JobStatus.COMPLETED) {
            return;
        }

        if (req.sourceSegments() != null && !req.sourceSegments().isEmpty()) {
            saveSourceSegments(job.getLesson(), req.detectedSourceLanguage(), req.sourceSegments());
        }
        if (req.targetSegments() != null && !req.targetSegments().isEmpty()) {
            saveTargetSegments(job.getLesson(), job.getTargetLanguage(), req.targetSegments());
        }
        if (req.audioFileUrl() != null) {
            saveAudioChunk(job, chunkIndex, req.startSec(), req.endSec(), req.audioFileUrl());
        }
    }

    @Transactional
    public void finish(Long jobId, FinishReq req) {
        AiJob job = loadJob(jobId);
        Long lessonId = job.getLesson().getId();
        String targetLanguage = job.getTargetLanguage();

        // BR-DUB-05: giải phóng CẢ HAI lớp chống trùng job ở nhánh kết thúc, dù thành công hay lỗi.
        dubbingLockService.release(lessonId, targetLanguage);
        job.releaseActiveFlag();
        job.setFinishedAt(LocalDateTime.now());

        switch (req.outcome()) {
            case "COMPLETED" -> completeJob(job, req);
            case "FAILED" -> failJob(job, req, TrackStatus.FAILED);
            case "SKIPPED" -> job.setStatus(JobStatus.SKIPPED); // BR-DUB-10: KHÔNG retry
            case "SOURCE_UNAVAILABLE" -> markSourceUnavailable(job, req);
            default -> throw new InvalidRequestException("outcome khong hop le: " + req.outcome());
        }
        aiJobRepository.save(job);
    }

    /** BR-DUB-11 — nguồn video không còn khả dụng: đánh dấu cả job lẫn Lesson. */
    private void markSourceUnavailable(AiJob job, FinishReq req) {
        failJob(job, req, TrackStatus.FAILED);
        Lesson lesson = job.getLesson();
        lesson.setStatus("UNAVAILABLE");
        lessonRepository.save(lesson);
    }

    private void completeJob(AiJob job, FinishReq req) {
        job.setStatus(JobStatus.COMPLETED);
        // Bình thường AudioTrack đã được `saveAudioChunk` tạo sẵn (PARTIAL) từ chunk COMPLETED
        // đầu tiên (BR-CHUNK-03). ai-worker chỉ gọi outcome=COMPLETED khi ÍT NHẤT MỘT chunk
        // lồng tiếng thật thành công — nếu MỌI chunk đều rơi vào nhánh giữ audio gốc
        // (BR-CHUNK-04, ví dụ Edge-TTS lỗi toàn bộ), ai-worker tự báo outcome=FAILED thay vì
        // COMPLETED (xem `dubbing_service.py::run_dubbing_pipeline`, nhánh `fallback_count ==
        // len(chunks)`) để tránh báo thành công giả khi thực chất chưa dịch được câu nào.
        AudioTrack track = findOrCreateAudioTrack(job);
        track.setFinalUrl(req.finalUrl());
        track.setDurationSec(req.durationSec());
        track.setFileSize(req.fileSize());
        track.setStatus(TrackStatus.COMPLETED);
        audioTrackRepository.save(track);

        notifyRequester(job, "DUBBING_COMPLETED", "Lồng tiếng hoàn tất",
                "Bản lồng tiếng " + job.getTargetLanguage() + " cho bài học đã sẵn sàng.");
    }

    private void failJob(AiJob job, FinishReq req, TrackStatus trackStatus) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(req.errorMessage());
        audioTrackRepository.findByLesson_IdAndLanguage(job.getLesson().getId(), job.getTargetLanguage())
                .ifPresent(track -> {
                    track.setStatus(trackStatus);
                    audioTrackRepository.save(track);
                });

        notifyRequester(job, "DUBBING_FAILED", "Lồng tiếng thất bại",
                "Không tạo được bản lồng tiếng " + job.getTargetLanguage() + " cho bài học này.");
    }

    /** BR-NOTIFY-01 — chỉ notify nếu biết ai đã yêu cầu (job trước F5.3 không có dữ liệu này). */
    private void notifyRequester(AiJob job, String type, String title, String content) {
        User requester = job.getRequestedBy();
        if (requester == null) {
            return;
        }
        String linkUrl = "/learn/" + job.getLesson().getId();
        notificationService.notify(requester.getId(), type, title, content, linkUrl);
    }

    /** BR-DUB-01 — lần đầu bài học được bóc băng: lưu Transcript(isSource=true) + điền Lesson.sourceLanguage. */
    private void saveSourceSegments(Lesson lesson, String detectedLanguage, List<SegmentDto> segments) {
        if (detectedLanguage == null || detectedLanguage.isBlank()) {
            throw new InvalidRequestException("detectedSourceLanguage bat buoc khi gui sourceSegments");
        }
        Transcript transcript = transcriptRepository.findByLesson_IdAndIsSourceTrue(lesson.getId())
                .orElseGet(() -> {
                    Transcript t = new Transcript();
                    t.setLesson(lesson);
                    t.setIsSource(true);
                    t.setLanguage(detectedLanguage);
                    return t;
                });
        appendSegments(transcript, segments);

        if (lesson.getSourceLanguage() == null) {
            lesson.setSourceLanguage(detectedLanguage);
            lessonRepository.save(lesson);
        }
    }

    private void saveTargetSegments(Lesson lesson, String targetLanguage, List<SegmentDto> segments) {
        Transcript transcript = transcriptRepository.findByLesson_IdAndLanguage(lesson.getId(), targetLanguage)
                .orElseGet(() -> {
                    Transcript t = new Transcript();
                    t.setLesson(lesson);
                    t.setIsSource(false);
                    t.setLanguage(targetLanguage);
                    return t;
                });
        appendSegments(transcript, segments);
    }

    private void appendSegments(Transcript transcript, List<SegmentDto> segments) {
        Transcript saved = transcriptRepository.save(transcript);
        StringBuilder appendedText = new StringBuilder();
        for (SegmentDto dto : segments) {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setTranscript(saved);
            segment.setSeq(dto.seq());
            segment.setStartSec(dto.startSec());
            segment.setEndSec(dto.endSec());
            segment.setText(dto.text());
            segment.setSpeechRate(dto.speechRate());
            if (dto.wasSummarized() != null) {
                segment.setWasSummarized(dto.wasSummarized());
            }
            transcriptSegmentRepository.save(segment);
            appendedText.append(dto.text()).append(' ');
        }
        saved.setFullText((saved.getFullText() == null ? "" : saved.getFullText() + " ") + appendedText.toString().trim());
        transcriptRepository.save(saved);
    }

    /** BR-CHUNK-03: chunk đầu tiên xong là AudioTrack đã ở trạng thái PARTIAL, học viên vào học được. */
    private void saveAudioChunk(AiJob job, Integer chunkIndex, Integer startSec, Integer endSec, String fileUrl) {
        AudioTrack track = findOrCreateAudioTrack(job);

        AudioChunk chunk = audioChunkRepository.findByAudioTrack_IdAndChunkIndex(track.getId(), chunkIndex)
                .orElseGet(AudioChunk::new);
        chunk.setAudioTrack(track);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartSec(startSec);
        chunk.setEndSec(endSec);
        chunk.setFileUrl(fileUrl);
        audioChunkRepository.save(chunk);
    }

    /** Tạo AudioTrack (PARTIAL) nếu (bài học, ngôn ngữ) chưa từng có — dùng chung cho cả nhánh
     * lưu chunk (BR-CHUNK-03) lẫn nhánh hoàn tất job (khi mọi chunk đều fallback audio gốc). */
    private AudioTrack findOrCreateAudioTrack(AiJob job) {
        Lesson lesson = job.getLesson();
        AudioTrack track = audioTrackRepository.findByLesson_IdAndLanguage(lesson.getId(), job.getTargetLanguage())
                .orElseGet(() -> {
                    AudioTrack t = new AudioTrack();
                    t.setLesson(lesson);
                    t.setLanguage(job.getTargetLanguage());
                    String voiceName = resolveVoiceName(job.getTargetLanguage());
                    VoiceMapping voiceMapping = voiceMappingRepository.findByLanguageAndIsActiveTrue(job.getTargetLanguage())
                            .stream()
                            .filter(v -> v.getVoiceName().equals(voiceName))
                            .findFirst()
                            .orElseThrow(() -> new BusinessRuleViolationException(
                                    "Khong con giong doc active cho ngon ngu " + job.getTargetLanguage()));
                    t.setVoiceName(voiceName);
                    t.setVoiceMapping(voiceMapping);
                    t.setStatus(TrackStatus.PARTIAL);
                    return t;
                });
        audioTrackRepository.save(track);
        return track;
    }

    /** BR-DUB-07 — ưu tiên giọng mặc định của ngôn ngữ, không có thì lấy giọng active đầu tiên. */
    private String resolveVoiceName(String targetLanguage) {
        List<VoiceMapping> voices = voiceMappingRepository.findByLanguageAndIsActiveTrue(targetLanguage);
        return voices.stream()
                .filter(VoiceMapping::getIsDefault)
                .findFirst()
                .or(() -> voices.stream().findFirst())
                .map(VoiceMapping::getVoiceName)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Khong con giong doc active cho ngon ngu " + targetLanguage));
    }

    private boolean isTerminal(JobStatus status) {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED;
    }

    private JobStatus parseChunkStatus(String raw) {
        JobStatus status;
        try {
            status = JobStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("status khong hop le: " + raw);
        }
        if (status != JobStatus.COMPLETED && status != JobStatus.FAILED) {
            throw new InvalidRequestException("Chunk chi nhan COMPLETED hoac FAILED, khong phai: " + raw);
        }
        return status;
    }

    private SegmentDto toSegmentDto(TranscriptSegment segment) {
        return new SegmentDto(segment.getSeq(), segment.getStartSec(), segment.getEndSec(), segment.getText(),
                segment.getSpeechRate(), segment.getWasSummarized());
    }

    private AiJob loadJob(Long jobId) {
        return aiJobRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("AiJob", jobId));
    }
}
