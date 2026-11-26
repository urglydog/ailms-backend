package com.lms.chat.service;

import com.lms.chat.dto.TutorInsightDto.LessonInsightRes;
import com.lms.chat.dto.TutorInsightDto.PoorTopicRes;
import com.lms.chat.dto.TutorInsightDto.TopicRes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phân tích THUẦN (không I/O) lịch sử câu hỏi Gia sư của 1 khoá thành Insight cho giảng viên
 * (26/11/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p><b>Gom chủ đề:</b> tách từ (bỏ từ dừng), mỗi bài tính "số học viên khác nhau dùng từ đó";
 * chữ ký của 1 câu hỏi = tối đa 2 từ phổ biến nhất (chỉ từ được &gt;= 2 học viên dùng). Các câu
 * cùng chữ ký là 1 chủ đề; chủ đề chỉ hiện khi &gt;= {@link #MIN_LEARNERS} học viên khác nhau hỏi.
 * Nhãn chủ đề CHỈ gồm các từ khoá chung đó — không bao giờ là câu nguyên văn của 1 người.
 *
 * <p><b>Trả lời chưa tốt:</b> (i) NOT_COVERED — câu trả lời của Gia sư chứa cụm "không đề cập /
 * ngoài phạm vi / không tìm thấy trong bài"; (ii) REPEAT — cùng 1 học viên hỏi lại cùng chủ đề
 * trong cùng bài trong vòng 24 giờ (dấu hiệu lần trước chưa hiểu).
 */
public final class TutorInsightAnalyzer {

    static final int MIN_LEARNERS = 2;
    static final int MAX_TOPICS_PER_LESSON = 5;
    static final int MAX_POOR_TOPICS = 10;
    static final int UNUSUAL_MIN = 3;

    private static final Set<String> STOP = Set.of(
            // tiếng Việt (đã bỏ dấu)
            "la", "gi", "cua", "va", "cho", "toi", "minh", "em", "anh", "chi", "ban", "thay", "co", "khong",
            "duoc", "nao", "the", "nay", "do", "nhu", "sao", "voi", "trong", "mot", "cac", "nhung", "de",
            "thi", "ve", "khi", "bi", "lam", "hay", "hoi", "giai", "thich", "oi", "a", "u", "vay", "nhi",
            "nhe", "day", "kia", "ai", "bai", "hoc", "giang", "video", "phan", "can", "muon", "biet", "hieu",
            "ro", "hon", "o", "ra", "vao", "len", "di", "den", "tu", "neu", "ma", "con", "se", "da", "dang",
            "roi", "cung", "nua", "vi", "nen", "tai", "sau", "truoc", "nhieu", "it", "rat", "qua",
            // tiếng Anh
            "an", "is", "are", "what", "how", "why", "of", "to", "in", "for", "and", "or",
            "you", "me", "this", "that", "does", "explain", "please", "about", "with", "on", "my");

    private static final Pattern NOT_COVERED = Pattern.compile(
            "(khong|chua) (duoc )?(de cap|nhac|trinh bay|noi)"
                    + "|nam ngoai (pham vi|noi dung)|ngoai pham vi"
                    + "|khong (tim thay|co) (thong tin |noi dung )?(trong|o) (bai|noi dung|transcript|video)"
                    + "|not covered|outside (the )?scope|not mentioned");

    /** 1 câu hỏi đã đọc từ DB. {@code lessonId} = bài Gia sư dùng làm ngữ cảnh (có thể null). */
    public record Question(String text, Long userId, Long lessonId, String aiText, LocalDateTime createdAt) {}

    public record Result(int totalQuestions, int learnerCount, List<LessonInsightRes> lessons,
                         List<PoorTopicRes> poorTopics) {}

    private TutorInsightAnalyzer() {}

    /**
     * @param lessons id → tiêu đề, theo thứ tự hiển thị của khoá (mọi bài, kể cả 0 câu hỏi)
     */
    public static Result analyze(List<Question> questions, Map<Long, String> lessons) {
        Set<Long> allUsers = new HashSet<>();
        Map<Long, List<Question>> byLesson = new LinkedHashMap<>();
        for (Question q : questions) {
            if (q.userId() != null) allUsers.add(q.userId());
            if (q.lessonId() != null && lessons.containsKey(q.lessonId())) {
                byLesson.computeIfAbsent(q.lessonId(), k -> new ArrayList<>()).add(q);
            }
        }
        int lessonQuestions = byLesson.values().stream().mapToInt(List::size).sum();
        double mean = lessons.isEmpty() ? 0 : (double) lessonQuestions / lessons.size();

        List<LessonInsightRes> lessonRes = new ArrayList<>();
        List<PoorTopicRes> poor = new ArrayList<>();
        for (Map.Entry<Long, String> lesson : lessons.entrySet()) {
            List<Question> qs = byLesson.get(lesson.getKey());
            if (qs == null || qs.isEmpty()) continue;
            LessonAnalysis la = analyzeLesson(qs);
            boolean unusual = lessons.size() >= 2 && qs.size() >= UNUSUAL_MIN && qs.size() >= 2 * mean;
            lessonRes.add(new LessonInsightRes(lesson.getKey(), lesson.getValue(), qs.size(), la.learners,
                    unusual, la.poorCount, la.topics));
            for (PoorGroup g : la.poorGroups) {
                poor.add(new PoorTopicRes(lesson.getKey(), lesson.getValue(), g.label, g.reason, g.users));
            }
        }
        poor.sort(Comparator.comparingInt(PoorTopicRes::learnerCount).reversed());
        return new Result(questions.size(), allUsers.size(), lessonRes,
                poor.size() > MAX_POOR_TOPICS ? poor.subList(0, MAX_POOR_TOPICS) : poor);
    }

    private record PoorGroup(String label, String reason, int users) {}

    private record LessonAnalysis(int learners, int poorCount, List<TopicRes> topics, List<PoorGroup> poorGroups) {}

    private static LessonAnalysis analyzeLesson(List<Question> qs) {
        // 1) tách từ + đếm số học viên khác nhau dùng mỗi từ
        List<List<String>> tokens = new ArrayList<>();
        Map<String, Set<Long>> tokenUsers = new HashMap<>();
        Map<String, Map<String, Integer>> surface = new HashMap<>();
        Map<String, double[]> position = new HashMap<>(); // [tổng vị trí tương đối, số lần]
        Set<Long> learners = new HashSet<>();
        for (Question q : qs) {
            if (q.userId() != null) learners.add(q.userId());
            List<String> keys = new ArrayList<>();
            String[] words = q.text() == null ? new String[0]
                    : q.text().toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+");
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (w.isEmpty()) continue;
                String key = TutorSuggestionService.normalize(w);
                if (key.length() < 2 || STOP.contains(key) || keys.contains(key)) continue;
                keys.add(key);
                if (q.userId() != null) tokenUsers.computeIfAbsent(key, k -> new HashSet<>()).add(q.userId());
                surface.computeIfAbsent(key, k -> new HashMap<>()).merge(w, 1, Integer::sum);
                double[] p = position.computeIfAbsent(key, k -> new double[2]);
                p[0] += words.length <= 1 ? 0 : (double) i / (words.length - 1);
                p[1] += 1;
            }
            tokens.add(keys);
        }
        // 2) chữ ký mỗi câu
        List<String> signatures = new ArrayList<>();
        for (List<String> keys : tokens) {
            List<String> shared = keys.stream()
                    .filter(k -> tokenUsers.getOrDefault(k, Set.of()).size() >= MIN_LEARNERS)
                    .sorted(Comparator.comparingInt((String k) -> -tokenUsers.get(k).size()).thenComparing(k -> k))
                    .limit(2)
                    .sorted()
                    .toList();
            signatures.add(shared.isEmpty() ? null : String.join(" ", shared));
        }
        // 3) gom chủ đề
        Map<String, Set<Long>> groupUsers = new LinkedHashMap<>();
        Map<String, Integer> groupCount = new HashMap<>();
        for (int i = 0; i < qs.size(); i++) {
            String sig = signatures.get(i);
            if (sig == null || qs.get(i).userId() == null) continue;
            groupUsers.computeIfAbsent(sig, k -> new HashSet<>()).add(qs.get(i).userId());
            groupCount.merge(sig, 1, Integer::sum);
        }
        List<TopicRes> topics = groupUsers.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_LEARNERS)
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<Long>> e) -> -e.getValue().size())
                        .thenComparing(e -> -groupCount.get(e.getKey())))
                .limit(MAX_TOPICS_PER_LESSON)
                .map(e -> new TopicRes(label(e.getKey(), surface, position), e.getValue().size(), groupCount.get(e.getKey())))
                .toList();
        // 4) trả lời chưa tốt
        int poorCount = 0;
        Map<String, Set<Long>> poorUsers = new LinkedHashMap<>();
        Map<String, LocalDateTime> lastAsk = new HashMap<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) order.add(i);
        order.sort(Comparator.comparing(i -> qs.get(i).createdAt() == null ? LocalDateTime.MIN : qs.get(i).createdAt()));
        for (int i : order) {
            Question q = qs.get(i);
            String sig = signatures.get(i);
            String reason = null;
            if (q.aiText() != null && NOT_COVERED.matcher(TutorSuggestionService.normalize(q.aiText())).find()) {
                reason = "NOT_COVERED";
            } else if (q.userId() != null && q.createdAt() != null && !tokens.get(i).isEmpty()) {
                // hỏi lặp xét theo chữ ký chung, hoặc (khi chưa ai khác hỏi) 2 từ khoá đầu của chính câu đó
                String own = sig != null ? sig
                        : String.join(" ", tokens.get(i).stream().limit(2).sorted().toList());
                String k = q.userId() + "|" + own;
                LocalDateTime prev = lastAsk.get(k);
                if (prev != null && !q.createdAt().isAfter(prev.plusHours(24))) reason = "REPEAT";
                lastAsk.put(k, q.createdAt());
            }
            if (reason == null) continue;
            poorCount++;
            if (sig != null && q.userId() != null) {
                poorUsers.computeIfAbsent(reason + "|" + sig, k -> new HashSet<>()).add(q.userId());
            }
        }
        List<PoorGroup> poorGroups = poorUsers.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_LEARNERS)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    return new PoorGroup(label(parts[1], surface, position), parts[0], e.getValue().size());
                })
                .toList();
        return new LessonAnalysis(learners.size(), poorCount, topics, poorGroups);
    }

    /** Nhãn = các từ khoá (dạng có dấu hay gặp nhất), xếp theo vị trí trung bình trong câu. */
    private static String label(String signature, Map<String, Map<String, Integer>> surface,
                                Map<String, double[]> position) {
        List<String> keys = new ArrayList<>(List.of(signature.split(" ")));
        keys.sort(Comparator.comparingDouble(k -> position.get(k)[0] / position.get(k)[1]));
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            out.add(surface.get(k).entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).orElse(k));
        }
        return String.join(" ", out);
    }
}
