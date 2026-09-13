package com.lms.dubbing.service;

import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.InvalidRequestException;
import com.lms.dubbing.dto.InternalDubbingDto.SegmentDto;
import com.lms.dubbing.entity.Transcript;
import com.lms.dubbing.entity.TranscriptSegment;
import com.lms.dubbing.repository.TranscriptRepository;
import com.lms.dubbing.repository.TranscriptSegmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi {@link Transcript}/{@link TranscriptSegment} — tách ra khỏi {@link InternalDubbingService}
 * vì giờ có 3 nguồn cùng cần ghi vào 2 bảng này: pipeline lồng tiếng (UC19), job trích script gốc
 * lúc nạp video ({@link TranscriptExtractionService}), và sinh học liệu dịch trước khi lồng tiếng
 * (UC24/25, {@code InternalMaterialService}) — dùng chung 1 chỗ để KHÔNG lặp lại logic
 * findOrCreate + append 3 nơi khác nhau.
 */
@Service
@RequiredArgsConstructor
public class TranscriptPersistenceService {

    private final TranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final LessonRepository lessonRepository;

    /** BR-DUB-01 — lần đầu bài học được bóc băng: lưu Transcript(isSource=true) + điền Lesson.sourceLanguage. */
    @Transactional
    public void saveSourceSegments(Lesson lesson, String detectedLanguage, List<SegmentDto> segments) {
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

    /**
     * Lưu bản dịch cho 1 ngôn ngữ đích — dùng chung cho cả pipeline lồng tiếng (UC19, sau bước
     * dịch Gemini) lẫn sinh học liệu (UC24/25, khi dịch trước cho ngôn ngữ chưa từng lồng tiếng)
     * để bản dịch đó TÁI SỬ DỤNG được giữa 2 luồng — đúng yêu cầu "tạo học liệu ngôn ngữ nào thì
     * dubbing ngôn ngữ đó sau này bỏ qua được bước dịch".
     */
    @Transactional
    public void saveTargetSegments(Lesson lesson, String targetLanguage, List<SegmentDto> segments) {
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
}
