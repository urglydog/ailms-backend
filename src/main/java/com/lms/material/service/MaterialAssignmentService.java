package com.lms.material.service;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.MaterialAssignment;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.MaterialAssignmentRepository;
import com.lms.material.repository.MaterialGenerationRepository;
import com.lms.material.repository.MindmapRepository;
import com.lms.material.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialAssignmentService {

    private final MaterialAssignmentRepository assignmentRepository;
    private final MaterialGenerationRepository generationRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final MindmapRepository mindmapRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;

    @Transactional
    public void assignMaterial(Long materialId, Long courseId, Long chapterId, Long lessonId) {
        MaterialGeneration material = generationRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", materialId));

        // Idempotency guard: tránh tạo assignment trùng lặp cho đúng cùng 1 đích
        // (lesson/chapter) nếu đã tồn tại — phòng trường hợp gọi lại nhiều lần.
        boolean alreadyExists = assignmentRepository.findByMaterial_Id(materialId).stream().anyMatch(a -> {
            Long existingChapterId = a.getChapter() != null ? a.getChapter().getId() : null;
            Long existingLessonId = a.getLesson() != null ? a.getLesson().getId() : null;
            return java.util.Objects.equals(existingChapterId, chapterId) && java.util.Objects.equals(existingLessonId, lessonId);
        });
        if (alreadyExists) {
            setOfficialStatus(materialId, true);
            return;
        }

        MaterialAssignment assignment = new MaterialAssignment();
        assignment.setMaterial(material);

        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
            assignment.setCourse(course);
        }
        if (chapterId != null) {
            Chapter chapter = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
            assignment.setChapter(chapter);
        }
        if (lessonId != null) {
            Lesson lesson = lessonRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
            assignment.setLesson(lesson);
        }

        assignmentRepository.save(assignment);

        // BR-OFFICIAL-01: Assigning a material to a course/chapter/lesson makes it visible
        // to students, so it must be marked Official at the same time (symmetric with the
        // revert-to-Draft logic in unassignMaterial/revertOfficialStatus below).
        if (courseId != null || chapterId != null || lessonId != null) {
            setOfficialStatus(materialId, true);
        }
    }

    /**
     * BR-OFFICIAL-01: Xóa một assignment. Nếu sau khi xóa, học liệu không còn
     * gắn với bất kỳ lesson/chapter nào, tự động revert isOfficial → false.
     * Đảm bảo trạng thái Official phản ánh đúng thực tế phân phối (Many-to-Many safe).
     */
    @Transactional
    public void unassignMaterial(Long assignmentId) {
        MaterialAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialAssignment", assignmentId));

        Long materialId = assignment.getMaterial() != null ? assignment.getMaterial().getId() : null;

        assignmentRepository.deleteById(assignmentId);
        // Flush so countByMaterial_Id reflects the deletion in the same transaction
        assignmentRepository.flush();

        // BR-OFFICIAL-01: Revert isOfficial only when this was the LAST assignment
        if (materialId != null && assignmentRepository.countByMaterial_Id(materialId) == 0) {
            setOfficialStatus(materialId, false);
        }
    }

    /**
     * Sets isOfficial on the underlying resource (Quiz / Mindmap / FlashcardDeck) for a material.
     * Used to flip Official on assign and revert to Draft when a material has zero remaining
     * lesson/chapter assignments (BR-OFFICIAL-01).
     */
    private void setOfficialStatus(Long materialId, boolean isOfficial) {
        generationRepository.findById(materialId).ifPresent(gen -> {
            switch (gen.getMaterialType()) {
                case QUIZ -> quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(materialId)
                        .ifPresent(quiz -> {
                            quiz.setIsOfficial(isOfficial);
                            quizRepository.save(quiz);
                        });
                case MINDMAP -> mindmapRepository.findByMaterialGeneration_Id(materialId)
                        .ifPresent(mm -> {
                            mm.setIsOfficial(isOfficial);
                            mindmapRepository.save(mm);
                        });
                case FLASHCARD -> flashcardDeckRepository.findByMaterialGeneration_Id(materialId)
                        .ifPresent(deck -> {
                            deck.setIsOfficial(isOfficial);
                            flashcardDeckRepository.save(deck);
                        });
            }
        });
    }

    @Transactional
    public void transferAssignments(Long oldMaterialId, Long newMaterialId) {
        generationRepository.findById(oldMaterialId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", oldMaterialId));
        MaterialGeneration newMaterial = generationRepository.findById(newMaterialId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", newMaterialId));

        for (MaterialAssignment a : assignmentRepository.findByMaterial_Id(oldMaterialId)) {
            a.setMaterial(newMaterial);
            assignmentRepository.save(a);
        }
    }
}
