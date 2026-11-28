package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.dto.TutorSuggestionDto.SuggestionRes;
import com.lms.chat.repository.ChatMessageRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thẻ câu hỏi gợi ý ở trạng thái rỗng của Gia sư AI (26/11/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p>Thứ tự ưu tiên: (i) POPULAR — câu hỏi học viên KHÁC đã hỏi Gia sư cho chính bài này
 * (lấy từ lịch sử {@code chat_messages}); (iii) TEMPLATE — mẫu điền tên bài/chương. Mức (ii) "AI
 * sinh từ nội dung bài" đọc từ cache {@link TutorAiSuggestionService} (sinh nền, không chặn
 * request). Thứ tự cuối: POPULAR &gt; AI &gt; TEMPLATE.
 *
 * <p><b>Riêng tư:</b> 1 câu hỏi chỉ được coi là "phổ biến" khi ÍT NHẤT {@link #MIN_DISTINCT_USERS}
 * học viên khác nhau (không tính người đang xem) hỏi cùng 1 câu (sau chuẩn hoá) — câu hỏi riêng
 * lẻ của 1 người (có thể chứa thông tin cá nhân) không bao giờ bị hiển thị cho người khác.
 */
@Service
@RequiredArgsConstructor
public class TutorSuggestionService {

    static final int MAX_SUGGESTIONS = 4;
    static final int MIN_DISTINCT_USERS = 2;
    static final int SCAN_LIMIT = 500;
    static final int MIN_LEN = 8;
    static final int MAX_LEN = 140;

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EnrollmentSecurity enrollmentSecurity;
    private final TutorAiSuggestionService aiSuggestionService;

    /**
     * Hiệu năng (28/11/2026): KHÔNG bọc @Transactional nữa. Trước đây giao dịch readOnly ngoài giữ
     * 1 kết nối DB trong khi {@code cachedOrSchedule} (REQUIRES_NEW) xin thêm kết nối thứ 2 → dưới
     * tải, 20 luồng giữ 20 kết nối cùng chờ kết nối thứ 2 = cạn pool, request treo tới timeout 5s.
     * Bài học được nạp sẵn chương + khoá (EntityGraph) nên không cần session mở.
     */
    public List<SuggestionRes> suggestions(String email, Long lessonId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Lesson lesson = lessonRepository.findWithChapterAndCourseById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (!enrollmentSecurity.canAccessLesson(email, lessonId, false)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }
        Long courseId = lesson.getChapter().getCourse().getId();

        List<SuggestionRes> out = new ArrayList<>(popular(courseId, lessonId, user.getId()));
        Set<String> seen = new HashSet<>();
        out.forEach(p -> seen.add(normalize(p.text())));
        for (String q : aiSuggestionService.cachedOrSchedule(lesson)) {
            if (out.size() >= MAX_SUGGESTIONS) break;
            if (seen.add(normalize(q))) out.add(new SuggestionRes(q, "AI", null, Map.of()));
        }
        for (SuggestionRes t : templates(lesson, previousLesson(courseId, lessonId))) {
            if (out.size() >= MAX_SUGGESTIONS) break;
            out.add(t);
        }
        return out;
    }

    /** Kết quả quét câu hỏi gần đây theo (khoá, bài) — dùng chung cho mọi học viên trong {@link #POPULAR_TTL_MS}. */
    static final long POPULAR_TTL_MS = 60_000;
    static final int POPULAR_CACHE_MAX = 2_000;
    private record ScanEntry(long expiresAt, List<Object[]> rows) {}
    private final Map<String, ScanEntry> popularScanCache = new ConcurrentHashMap<>();

    /** Hiệu năng (28/11/2026): truy vấn quét tới {@link #SCAN_LIMIT} câu hỏi (subquery tương quan)
     * chạy MỖI lần học viên mở bài — nay cache 60s theo bài; lọc người xem vẫn làm theo từng request. */
    private List<Object[]> recentQuestionRows(Long courseId, Long lessonId) {
        String key = courseId + ":" + lessonId;
        long now = System.currentTimeMillis();
        ScanEntry e = popularScanCache.get(key);
        if (e != null && e.expiresAt() > now) return e.rows();
        List<Object[]> rows = chatMessageRepository.findRecentQuestionsForLesson(courseId, lessonId, SCAN_LIMIT);
        if (popularScanCache.size() >= POPULAR_CACHE_MAX) popularScanCache.clear();
        popularScanCache.put(key, new ScanEntry(now + POPULAR_TTL_MS, rows));
        return rows;
    }

    List<SuggestionRes> popular(Long courseId, Long lessonId, Long viewerId) {
        Map<String, String> firstText = new LinkedHashMap<>();
        Map<String, Set<Long>> askers = new LinkedHashMap<>();
        for (Object[] row : recentQuestionRows(courseId, lessonId)) {
            String text = row[0] == null ? "" : row[0].toString().trim().replaceAll("\\s+", " ");
            Long uid = row[1] == null ? null : ((Number) row[1]).longValue();
            if (uid == null || uid.equals(viewerId) || text.length() < MIN_LEN || text.length() > MAX_LEN) continue;
            String key = normalize(text);
            firstText.putIfAbsent(key, text);
            askers.computeIfAbsent(key, k -> new HashSet<>()).add(uid);
        }
        return askers.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_DISTINCT_USERS)
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .limit(MAX_SUGGESTIONS)
                .map(e -> new SuggestionRes(firstText.get(e.getKey()), "POPULAR", null, Map.of()))
                .toList();
    }

    static List<SuggestionRes> templates(Lesson lesson, Lesson previous) {
        String x = lesson.getTitle();
        List<SuggestionRes> list = new ArrayList<>();
        list.add(new SuggestionRes("Tóm tắt bài " + x, "TEMPLATE", "summary", Map.of("lesson", x)));
        list.add(new SuggestionRes("Đố tôi 3 câu về " + x, "TEMPLATE", "quiz", Map.of("lesson", x)));
        list.add(new SuggestionRes("Giải thích khái niệm khó nhất trong " + x, "TEMPLATE", "hardest", Map.of("lesson", x)));
        if (previous != null) {
            String p = previous.getTitle();
            list.add(new SuggestionRes("Bài này liên quan gì tới " + p, "TEMPLATE", "relation",
                    Map.of("lesson", x, "previous", p)));
        } else {
            String ch = lesson.getChapter().getTitle() == null ? x : lesson.getChapter().getTitle();
            list.add(new SuggestionRes("Ý chính của chương " + ch + " là gì?", "TEMPLATE", "chapter",
                    Map.of("lesson", x, "chapter", ch)));
        }
        return list;
    }

    private Lesson previousLesson(Long courseId, Long lessonId) {
        List<Lesson> ordered = lessonRepository.findByChapter_CourseIdOrderByChapter_DisplayOrderAscDisplayOrderAsc(courseId);
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(lessonId)) return ordered.get(i - 1);
        }
        return null;
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return n.replaceAll("[^\\p{L}\\p{N} ]", "").replaceAll("\\s+", " ").trim();
    }
}
