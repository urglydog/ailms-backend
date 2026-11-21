package com.lms.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.MaterialType;
import com.lms.common.enums.QuizType;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.material.dto.InternalMaterialDto.FinishReq;
import com.lms.material.dto.InternalMaterialDto.QuizDto;
import com.lms.material.dto.QuizDto.QuizSettingsReq;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.entity.Quiz;
import com.lms.material.repository.MaterialGenerationRepository;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Đề thi chính thức do giảng viên tạo bằng AI phải ra OFFICIAL_EXAM (trước đây luôn LECTURE_QUIZ nên mở như Quick Check) và đổi loại có kiểm soát. */
@ExtendWith(MockitoExtension.class)
class QuizTypeOfficialExamTest {

    @Mock private MaterialGenerationRepository materialGenerationRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private com.lms.material.repository.QuizQuestionRepository quizQuestionRepository;
    @Mock private com.lms.material.repository.QuizOptionRepository quizOptionRepository;
    @Mock private com.lms.common.repository.AiUsageLogRepository aiUsageLogRepository;
    @Mock private com.lms.common.service.AiPricing aiPricing;
    @InjectMocks private InternalMaterialService internal;

    private MaterialGeneration quizGeneration(QuizType type) {
        User u = new User();
        u.setId(1L);
        MaterialGeneration g = new MaterialGeneration();
        g.setUser(u);
        g.setMaterialType(MaterialType.QUIZ);
        g.setQuizType(type);
        return g;
    }

    private QuizType finishAndGetSavedType(MaterialGeneration g) {
        when(materialGenerationRepository.findById(5L)).thenReturn(Optional.of(g));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.lenient().when(quizQuestionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        internal.finish(5L, new FinishReq("COMPLETED", null, null, null,
                List.of(new QuizDto("Câu hỏi?", List.of("A", "B", "C", "D"), "A")), null));
        ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(saved.capture());
        return saved.getValue().getQuizType();
    }

    @Test
    void instructorOfficialExamChoiceIsKeptWhenTheAiFinishes() {
        assertThat(finishAndGetSavedType(quizGeneration(QuizType.OFFICIAL_EXAM))).isEqualTo(QuizType.OFFICIAL_EXAM);
    }

    @Test
    void noChoiceMeansQuickCheckAsBefore() {
        assertThat(finishAndGetSavedType(quizGeneration(null))).isEqualTo(QuizType.LECTURE_QUIZ);
    }

    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private com.lms.auth.repository.UserRepository userRepository;
    @InjectMocks private QuizService quizService;

    private Quiz quizOf(QuizType type) {
        Course course = new Course();
        User instructor = new User();
        instructor.setEmail("gv@lms.local");
        course.setInstructor(instructor);
        MaterialGeneration g = new MaterialGeneration();
        g.setCourse(course);
        Quiz q = new Quiz();
        q.setMaterialGeneration(g);
        q.setQuizType(type);
        return q;
    }

    private QuizSettingsReq settings(QuizType type) {
        return new QuizSettingsReq(5, true, null, null, 15, 1, true, 3, type);
    }

    @Test
    void instructorCanSwitchAQuizWithoutAttemptsToOfficialExam() {
        Quiz quiz = quizOf(QuizType.LECTURE_QUIZ);
        when(quizRepository.findById(9L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.countByQuiz_Id(9L)).thenReturn(0);

        quizService.updateQuizSettings("gv@lms.local", 9L, settings(QuizType.OFFICIAL_EXAM));

        assertThat(quiz.getQuizType()).isEqualTo(QuizType.OFFICIAL_EXAM);
        // Sau khi là thi chính thức, giờ/số lượt/giám sát đã đặt có hiệu lực (trước đó getter của Quick Check che mất).
        assertThat(quiz.getDurationMinutes()).isEqualTo(15);
        assertThat(quiz.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void switchingTheTypeIsBlockedOnceStudentsHaveAttempts() {
        Quiz quiz = quizOf(QuizType.OFFICIAL_EXAM);
        when(quizRepository.findById(9L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.countByQuiz_Id(9L)).thenReturn(3);

        assertThatThrownBy(() -> quizService.updateQuizSettings("gv@lms.local", 9L, settings(QuizType.LECTURE_QUIZ)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("đã có học viên làm");
        verify(quizRepository, never()).save(any());
        assertThat(quiz.getQuizType()).isEqualTo(QuizType.OFFICIAL_EXAM);
    }

    @Test
    void leavingTheTypeOutKeepsItAndSavingOtherSettingsStillWorksWithAttempts() {
        Quiz quiz = quizOf(QuizType.OFFICIAL_EXAM);
        when(quizRepository.findById(9L)).thenReturn(Optional.of(quiz));

        quizService.updateQuizSettings("gv@lms.local", 9L, settings(null));

        assertThat(quiz.getQuizType()).isEqualTo(QuizType.OFFICIAL_EXAM);
        verify(quizRepository).save(quiz);
    }
}
