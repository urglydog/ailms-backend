package com.lms.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lms.catalog.entity.Course;
import com.lms.common.enums.MaterialType;
import com.lms.common.enums.QuantityLevel;
import com.lms.common.enums.DifficultyLevel;
import com.lms.common.enums.ScopeType;
import com.lms.dubbing.repository.TranscriptRepository;
import com.lms.dubbing.repository.TranscriptSegmentRepository;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.repository.MaterialGenerationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Ngữ cảnh gửi cho AI Worker phải kèm số bài của cả khóa để worker tính tỷ lệ số câu/thẻ (BR-MAT-05). */
@ExtendWith(MockitoExtension.class)
class InternalMaterialContextTest {

    @Mock private MaterialGenerationRepository materialGenerationRepository;
    @Mock private TranscriptSegmentRepository transcriptSegmentRepository;
    @Mock private TranscriptRepository transcriptRepository;
    @InjectMocks private InternalMaterialService service;

    @Test
    void getContext_includesTheCourseLessonCountAndTheChosenLevels() {
        Course course = new Course();
        course.setId(92L);
        course.setTitle("Khóa A");
        MaterialGeneration g = new MaterialGeneration();
        g.setCourse(course);
        g.setMaterialType(MaterialType.QUIZ);
        g.setLanguage("ja-JP");
        g.setScopeType(ScopeType.WHOLE_COURSE);
        g.setQuantityLevel(QuantityLevel.MORE);
        g.setDifficultyLevel(DifficultyLevel.HARD);
        when(materialGenerationRepository.findById(7L)).thenReturn(Optional.of(g));
        when(transcriptSegmentRepository.findByCourseIdAndIsSourceTrue(92L)).thenReturn(List.of());
        when(transcriptRepository.countLessonsWithSourceTranscriptByCourseId(92L)).thenReturn(4L);

        var res = service.getContext(7L);

        assertThat(res.courseLessonCount()).isEqualTo(4L);
        assertThat(res.quantityLevel()).isEqualTo("MORE");
        assertThat(res.difficultyLevel()).isEqualTo("HARD");
    }
}
