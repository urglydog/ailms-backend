package com.lms.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.chat.entity.LessonAiSuggestion;
import com.lms.chat.repository.LessonAiSuggestionRepository;
import com.lms.common.config.AiWorkerConfig;
import com.lms.dubbing.repository.TranscriptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorAiSuggestionServiceTest {

    @Mock private LessonAiSuggestionRepository repository;
    @Mock private LessonRepository lessonRepository;
    @Mock private TranscriptRepository transcriptRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;

    private TutorAiSuggestionService service;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new TutorAiSuggestionService(repository, lessonRepository, transcriptRepository,
                restTemplate, aiWorkerConfig, new ObjectMapper());
        Chapter ch = new Chapter();
        ch.setTitle("Chuong 1");
        lesson = new Lesson();
        lesson.setId(10L);
        lesson.setTitle("Thi hien tai");
        lesson.setChapter(ch);
        lenient().when(transcriptRepository.findSourceFingerprint(10L)).thenReturn(Optional.of("3:1200"));
        lenient().when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai");
    }

    private LessonAiSuggestion row(String status, String fp) {
        LessonAiSuggestion r = new LessonAiSuggestion();
        r.setLessonId(10L);
        r.setStatus(status);
        r.setFingerprint(fp);
        return r;
    }

    @Test
    void noCache_createsPendingRow_returnsEmpty_withoutCallingLlm() {
        when(repository.findByLessonId(10L)).thenReturn(Optional.empty());

        assertThat(service.cachedOrSchedule(lesson)).isEmpty();

        ArgumentCaptor<LessonAiSuggestion> saved = ArgumentCaptor.forClass(LessonAiSuggestion.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(LessonAiSuggestion.PENDING);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void readyAndFingerprintMatches_returnsCachedQuestions() {
        String fp = TutorAiSuggestionService.fingerprint(lesson, "3:1200");
        LessonAiSuggestion r = row(LessonAiSuggestion.READY, fp);
        r.setQuestions("[\"Cau hoi mot la gi?\",\"Cau hoi hai la gi?\"]");
        when(repository.findByLessonId(10L)).thenReturn(Optional.of(r));

        assertThat(service.cachedOrSchedule(lesson)).containsExactly("Cau hoi mot la gi?", "Cau hoi hai la gi?");
        verify(repository, never()).save(any());
    }

    @Test
    void contentChanged_invalidatesCache_backToPending() {
        String oldFp = TutorAiSuggestionService.fingerprint(lesson, "3:900");
        LessonAiSuggestion r = row(LessonAiSuggestion.READY, oldFp);
        r.setQuestions("[\"Cu roi nhe ban oi?\"]");
        when(repository.findByLessonId(10L)).thenReturn(Optional.of(r));

        assertThat(service.cachedOrSchedule(lesson)).isEmpty();
        assertThat(r.getStatus()).isEqualTo(LessonAiSuggestion.PENDING);
        assertThat(r.getQuestions()).isNull();
        assertThat(r.getFingerprint()).isEqualTo(TutorAiSuggestionService.fingerprint(lesson, "3:1200"));
    }

    @Test
    void failedRecently_isNotRetriedYet_butOldFailureIs() {
        String fp = TutorAiSuggestionService.fingerprint(lesson, "3:1200");
        LessonAiSuggestion r = row(LessonAiSuggestion.FAILED, fp);
        r.setAttempts(1);
        r.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        when(repository.findByLessonId(10L)).thenReturn(Optional.of(r));
        service.cachedOrSchedule(lesson);
        assertThat(r.getStatus()).isEqualTo(LessonAiSuggestion.FAILED);

        r.setUpdatedAt(LocalDateTime.now().minusHours(2));
        service.cachedOrSchedule(lesson);
        assertThat(r.getStatus()).isEqualTo(LessonAiSuggestion.PENDING);
    }

    @Test
    void generate_storesCleanedQuestions_andFailureCountsAttempt() {
        LessonAiSuggestion r = row(LessonAiSuggestion.PENDING, "x");
        when(lessonRepository.findById(10L)).thenReturn(Optional.of(lesson));
        when(transcriptRepository.findByLesson_IdAndIsSourceTrue(10L)).thenReturn(Optional.empty());
        when(restTemplate.postForObject(eq("http://ai/api/v1/tutor/suggestions"), any(),
                eq(TutorAiSuggestionService.AiWorkerSuggestionsRes.class)))
                .thenReturn(new TutorAiSuggestionService.AiWorkerSuggestionsRes(
                        List.of("  Vi sao   dung thi nay?  ", "ngan", "Vi sao dung thi nay?")));

        service.generate(r);
        assertThat(r.getStatus()).isEqualTo(LessonAiSuggestion.READY);
        assertThat(r.getQuestions()).isEqualTo("[\"Vi sao dung thi nay?\"]");

        LessonAiSuggestion r2 = row(LessonAiSuggestion.PENDING, "x");
        when(restTemplate.postForObject(anyString(), any(), eq(TutorAiSuggestionService.AiWorkerSuggestionsRes.class)))
                .thenThrow(new RestClientException("down"));
        service.generate(r2);
        assertThat(r2.getStatus()).isEqualTo(LessonAiSuggestion.FAILED);
        assertThat(r2.getAttempts()).isEqualTo(1);
    }
}
