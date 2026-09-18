package com.lms.material.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.dto.MaterialFolderDto;
import com.lms.material.dto.MaterialFolderReq;
import com.lms.material.entity.MaterialFolder;
import com.lms.material.repository.MaterialFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialFolderService {

    private final MaterialFolderRepository materialFolderRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<MaterialFolderDto> getFoldersByCourse(Long courseId, Long parentId) {
        List<MaterialFolder> folders;
        if (parentId != null) {
            folders = materialFolderRepository.findByCourse_IdAndParent_IdOrderByCreatedAtAsc(courseId, parentId);
        } else {
            folders = materialFolderRepository.findByCourse_IdAndParentIsNullOrderByCreatedAtAsc(courseId);
        }
        return folders.stream().map(this::toDto).toList();
    }

    @Transactional
    public MaterialFolderDto createFolder(MaterialFolderReq req) {
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));
        
        MaterialFolder folder = new MaterialFolder();
        folder.setName(req.name());
        folder.setCourse(course);
        
        if (req.parentId() != null) {
            MaterialFolder parent = materialFolderRepository.findById(req.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MaterialFolder", req.parentId()));
            folder.setParent(parent);
        }
        
        return toDto(materialFolderRepository.save(folder));
    }

    @Transactional
    public MaterialFolderDto updateFolder(Long id, MaterialFolderReq req) {
        MaterialFolder folder = materialFolderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialFolder", id));
        folder.setName(req.name());
        return toDto(materialFolderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(Long id) {
        if (!materialFolderRepository.existsById(id)) {
            throw new ResourceNotFoundException("MaterialFolder", id);
        }
        materialFolderRepository.deleteById(id);
    }

    private MaterialFolderDto toDto(MaterialFolder folder) {
        return new MaterialFolderDto(
                folder.getId(),
                folder.getName(),
                folder.getCourse().getId(),
                folder.getParent() != null ? folder.getParent().getId() : null,
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
