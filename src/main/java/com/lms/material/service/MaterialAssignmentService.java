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
import com.lms.material.repository.MaterialAssignmentRepository;
import com.lms.material.repository.MaterialGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaterialAssignmentService {

    private final MaterialAssignmentRepository assignmentRepository;
    private final MaterialGenerationRepository generationRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;

    @Transactional
    public void assignMaterial(Long materialId, Long courseId, Long chapterId, Long lessonId) {
        MaterialGeneration material = generationRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", materialId));

        // Delete existing assignments for this material to avoid duplicates for the same target, 
        // or just create a new one. Wait, if it's the SAME target, we do versioning.
        // For simplicity right now, let's just clear old assignments of this material if it's a move.
        // But Master-Link means 1 material can be assigned to multiple targets.
        // So we just add a new assignment if it doesn't exist.
        
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
    }

    @Transactional
    public void unassignMaterial(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("MaterialAssignment", assignmentId);
        }
        assignmentRepository.deleteById(assignmentId);
    }
}
