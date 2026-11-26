package com.lms.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.entity.LessonAiSuggestion;
import com.lms.chat.repository.LessonAiSuggestionRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.dubbing.entity.Transcript;
import com.lms.dubbing.repository.TranscriptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Nguồn AI của câu hỏi gợi ý Gia sư (26/11/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p><b>Không bao giờ chặn request chờ LLM:</b> {@link #cachedOrSchedule} chỉ đọc cache; thiếu
 * hoặc cũ thì đánh dấu PENDING và trả rỗng ngay — job {@link #processPending} (chạy nền mỗi 20s,
 * khoá ShedLock) mới gọi AI Worker {@code POST /api/v1/tutor/suggestions}.
 *
 * <p><b>Vô hiệu hoá cache:</b> {@link #fingerprint} băm tiêu đề/mô tả bài + tiêu đề chương +
 * "id:độ dài" transcript gốc — nội dung đổi thì dấu vân tay lệch và câu hỏi được sinh lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorAiSuggestionService {

    static final int MAX_ATTEMPTS = 3;
    static final int RETRY_AFTER_MINUTES = 30;
    static final int BATCH = 5;
    static final int TRANSCRIPT_EXCERPT = 6000;
    static final int MAX_QUESTIONS = 6;

    private final LessonAiSuggestionRepository repository;
    private final LessonRepository lessonRepository;
    private final TranscriptRepository transcriptRepository;
    private final RestTemplate restTemplate;
    private final AiWorkerConfig aiWorkerConfig;
    private final ObjectMapper objectMapper;

    /** Câu hỏi AI đã sẵn sàng cho bài; chưa có/cũ thì xếp hàng sinh nền và trả rỗng. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> cachedOrSchedule(Lesson lesson) {
        String fp = fingerprint(lesson, transcriptRepository.findSourceFingerprint(lesson.getId()).orElse(""));
        Optional<LessonAiSuggestion> existing = repository.findByLessonId(lesson.getId());
        if (existing.isEmpty()) {
            LessonAiSuggestion row = new LessonAiSuggestion();
            row.setLessonId(lesson.getId());
            row.setFingerprint(fp);
            try {
                repository.saveAndFlush(row);
            } catch (DataIntegrityViolationException race) {
                // request song song đã tạo dòng — bỏ qua
            }
            return List.of();
        }
        LessonAiSuggestion row = existing.get();
        if (!fp.equals(row.getFingerprint())) {
            row.setFingerprint(fp);
            row.setStatus(LessonAiSuggestion.PENDING);
            row.setAttempts(0);
            row.setQuestions(null);
            repository.save(row);
            return List.of();
        }
        if (LessonAiSuggestion.READY.equals(row.getStatus())) {
            return parse(row.getQuestions());
        }
        if (LessonAiSuggestion.FAILED.equals(row.getStatus()) && row.getAttempts() < MAX_ATTEMPTS
                && row.getUpdatedAt() != null
                && row.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(RETRY_AFTER_MINUTES))) {
            row.setStatus(LessonAiSuggestion.PENDING);
            repository.save(row);
        }
        return List.of();
    }

    /** Job nền — xử lý tối đa {@link #BATCH} dòng PENDING mỗi lượt. */
    @Scheduled(fixedDelay = 20_000, initialDelay = 60_000)
    @SchedulerLock(name = "TutorAiSuggestionService.processPending", lockAtMostFor = "PT5M", lockAtLeastFor = "PT16S")
    @Transactional
    public void processPending() {
        for (LessonAiSuggestion row : repository.findByStatusOrderByUpdatedAtAsc(
                LessonAiSuggestion.PENDING, PageRequest.of(0, BATCH))) {
            generate(row);
        }
    }

    void generate(LessonAiSuggestion row) {
        Lesson lesson = lessonRepository.findById(row.getLessonId()).orElse(null);
        if (lesson == null) {
            repository.delete(row);
            return;
        }
        try {
            String excerpt = transcriptRepository.findByLesson_IdAndIsSourceTrue(lesson.getId())
                    .map(Transcript::getFullText)
                    .map(t -> t.length() > TRANSCRIPT_EXCERPT ? t.substring(0, TRANSCRIPT_EXCERPT) : t)
                    .orElse("");
            String chapter = lesson.getChapter() == null || lesson.getChapter().getTitle() == null
                    ? "" : lesson.getChapter().getTitle();
            Map<String, Object> payload = Map.of(
                    "lesson_title", lesson.getTitle(),
                    "chapter_title", chapter,
                    "description", lesson.getDescription() == null ? "" : lesson.getDescription(),
                    "transcript_excerpt", excerpt,
                    "count", MAX_QUESTIONS);
            AiWorkerSuggestionsRes res = restTemplate.postForObject(
                    aiWorkerConfig.getBaseUrl() + "/api/v1/tutor/suggestions", payload, AiWorkerSuggestionsRes.class);
            List<String> questions = clean(res == null ? null : res.questions());
            if (questions.isEmpty()) {
                throw new IllegalStateException("AI Worker trả danh sách rỗng");
            }
            row.setQuestions(objectMapper.writeValueAsString(questions));
            row.setStatus(LessonAiSuggestion.READY);
        } catch (Exception exc) {
            log.warn("Sinh câu hỏi gợi ý AI thất bại cho bài {}: {}", row.getLessonId(), exc.getMessage());
            row.setAttempts(row.getAttempts() + 1);
            row.setStatus(LessonAiSuggestion.FAILED);
        }
        repository.save(row);
    }

    static List<String> clean(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String q : raw) {
            if (q == null) continue;
            String t = q.trim().replaceAll("\\s+", " ");
            if (t.length() < 8 || t.length() > 160 || out.contains(t)) continue;
            out.add(t);
            if (out.size() >= MAX_QUESTIONS) break;
        }
        return out;
    }

    List<String> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exc) {
            return List.of();
        }
    }

    static String fingerprint(Lesson lesson, String transcriptFp) {
        String raw = String.join("\u0001",
                nz(lesson.getTitle()), nz(lesson.getDescription()),
                lesson.getChapter() == null ? "" : nz(lesson.getChapter().getTitle()), nz(transcriptFp));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Khớp response {@code POST /api/v1/tutor/suggestions} của AI Worker. */
    record AiWorkerSuggestionsRes(List<String> questions) {}
}
