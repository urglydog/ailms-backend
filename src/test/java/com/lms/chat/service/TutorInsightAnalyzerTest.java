package com.lms.chat.service;

import com.lms.chat.dto.TutorInsightDto.LessonInsightRes;
import com.lms.chat.service.TutorInsightAnalyzer.Question;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TutorInsightAnalyzerTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 20, 10, 0);

    private static Map<Long, String> lessons() {
        Map<Long, String> m = new LinkedHashMap<>();
        m.put(1L, "Bai 1");
        m.put(2L, "Bai 2");
        m.put(3L, "Bai 3");
        return m;
    }

    private static Question q(String text, long user, long lesson, String ai, int minutes) {
        return new Question(text, user, lesson, ai, T0.plusMinutes(minutes));
    }

    @Test
    void topic_requiresTwoDistinctLearners_andLabelIsKeywordsNotVerbatim() {
        List<Question> qs = List.of(
                q("Vòng lặp for khác while thế nào?", 1, 1, "ok", 0),
                q("Khi nào dùng vòng lặp while?", 2, 1, "ok", 1),
                q("Mật khẩu của tôi là abc123 có an toàn không?", 3, 1, "ok", 2));

        var r = TutorInsightAnalyzer.analyze(qs, lessons());

        LessonInsightRes l1 = r.lessons().get(0);
        assertThat(l1.questionCount()).isEqualTo(3);
        assertThat(l1.learnerCount()).isEqualTo(3);
        assertThat(l1.topics()).hasSize(1);
        assertThat(l1.topics().get(0).learnerCount()).isEqualTo(2);
        String label = l1.topics().get(0).label();
        assertThat(label).isEqualTo("vòng lặp");
        assertThat(label).doesNotContain("?").isNotEqualTo("Khi nào dùng vòng lặp while?");
        // câu riêng lẻ của 1 người không bao giờ lộ ra
        assertThat(l1.topics()).noneMatch(t -> t.label().contains("abc123") || t.label().contains("khẩu"));
    }

    @Test
    void sameUserAskingManyTimes_isNotATopic() {
        List<Question> qs = List.of(
                q("Đệ quy là gì?", 1, 1, "ok", 0),
                q("Đệ quy là gì vậy?", 1, 1, "ok", 1),
                q("Đệ quy dùng khi nào?", 1, 1, "ok", 2));

        var r = TutorInsightAnalyzer.analyze(qs, lessons());

        assertThat(r.lessons().get(0).topics()).isEmpty();
        assertThat(r.poorTopics()).isEmpty(); // REPEAT đếm được nhưng nhóm cần >= 2 học viên
        assertThat(r.lessons().get(0).poorCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unusualLesson_andNotCoveredAnswers_areDetected() {
        List<Question> qs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            qs.add(q("Con trỏ hàm trong C hoạt động ra sao " + i, 10 + i, 2,
                    "Nội dung này không được đề cập trong bài giảng.", i));
        }
        qs.add(q("Biến cục bộ là gì?", 1, 3, "ok", 0));

        var r = TutorInsightAnalyzer.analyze(qs, lessons());

        LessonInsightRes l2 = r.lessons().stream().filter(l -> l.lessonId() == 2L).findFirst().orElseThrow();
        LessonInsightRes l3 = r.lessons().stream().filter(l -> l.lessonId() == 3L).findFirst().orElseThrow();
        assertThat(l2.unusual()).isTrue();
        assertThat(l3.unusual()).isFalse();
        assertThat(l2.poorCount()).isEqualTo(6);
        assertThat(r.poorTopics()).isNotEmpty();
        assertThat(r.poorTopics().get(0).reason()).isEqualTo("NOT_COVERED");
        assertThat(r.poorTopics().get(0).learnerCount()).isEqualTo(6);
        assertThat(r.totalQuestions()).isEqualTo(7);
        assertThat(r.learnerCount()).isEqualTo(7);
    }

    @Test
    void repeatAsksByTwoLearners_formPoorTopic() {
        List<Question> qs = List.of(
                q("Giải thích hàm băm SHA", 1, 1, "ok", 0),
                q("Vẫn chưa rõ hàm băm SHA", 1, 1, "ok", 30),
                q("Hàm băm SHA dùng làm gì", 2, 1, "ok", 0),
                q("Hàm băm SHA an toàn không", 2, 1, "ok", 60));

        var r = TutorInsightAnalyzer.analyze(qs, lessons());

        assertThat(r.poorTopics()).hasSize(1);
        assertThat(r.poorTopics().get(0).reason()).isEqualTo("REPEAT");
        assertThat(r.poorTopics().get(0).learnerCount()).isEqualTo(2);
    }

    @Test
    void lessonsWithoutQuestions_areOmitted_andUnknownLessonIgnored() {
        var r = TutorInsightAnalyzer.analyze(List.of(q("Một câu hỏi bất kỳ", 1, 99, "ok", 0)), lessons());
        assertThat(r.lessons()).isEmpty();
        assertThat(r.totalQuestions()).isEqualTo(1);
    }
}
