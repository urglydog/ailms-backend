package com.lms.material.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.dto.MaterialFolderDto;
import com.lms.material.dto.MaterialFolderReq;
import com.lms.material.entity.MaterialFolder;
import com.lms.material.repository.MaterialFolderRepository;
import com.lms.material.repository.MaterialGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialFolderService {

    private final MaterialFolderRepository materialFolderRepository;
    private final CourseRepository courseRepository;
    private final MaterialGenerationRepository materialGenerationRepository;
    private final com.lms.catalog.service.CourseActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public List<MaterialFolderDto> getFoldersByCourse(Long courseId, Long parentId) {
        List<MaterialFolder> folders;
        if (parentId != null) {
            folders = materialFolderRepository.findByCourse_IdAndParent_IdOrderByCreatedAtAsc(courseId, parentId);
        } else {
            // FE builds the folder tree client-side from a single flat fetch, so return the
            // full folder list for the course (not just root-level folders) when parentId is absent.
            folders = materialFolderRepository.findByCourse_IdOrderByCreatedAtAsc(courseId);
        }
        return folders.stream().map(this::toDto).toList();
    }

    @Transactional
    public MaterialFolderDto createFolder(MaterialFolderReq req, String actorEmail) {
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

        MaterialFolderDto saved = toDto(materialFolderRepository.save(folder));
        activityLogService.log(course, actorEmail, "Đã tạo thư mục \"" + req.name() + "\"");
        return saved;
    }

    @Transactional
    public MaterialFolderDto updateFolder(Long id, MaterialFolderReq req) {
        MaterialFolder folder = materialFolderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialFolder", id));
        folder.setName(req.name());
        return toDto(materialFolderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(Long id, String actorEmail) {
        MaterialFolder folder = materialFolderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialFolder", id));
        // Trước khi xóa: detach tất cả học liệu trong thư mục này về root (folder_id = null)
        // để tránh lỗi FK constraint khi JPA/DB không tự SET NULL trong transactional context
        materialGenerationRepository.findByFolder_Id(id)
                .forEach(gen -> {
                    gen.setFolder(null);
                    materialGenerationRepository.save(gen);
                });
        // Detach sub-folders: đặt parent = null cho các thư mục con (ON DELETE SET NULL ở DB)
        materialFolderRepository.findByCourse_IdAndParent_IdOrderByCreatedAtAsc(
                folder.getCourse().getId(), id
        ).forEach(child -> {
            child.setParent(null);
            materialFolderRepository.save(child);
        });
        activityLogService.log(folder.getCourse(), actorEmail, "Đã xóa thư mục \"" + folder.getName() + "\"");
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
