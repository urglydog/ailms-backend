package com.lms.material.repository;

import com.lms.material.entity.MaterialFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialFolderRepository extends JpaRepository<MaterialFolder, Long> {
    List<MaterialFolder> findByCourse_IdOrderByCreatedAtAsc(Long courseId);
    List<MaterialFolder> findByCourse_IdAndParent_IdOrderByCreatedAtAsc(Long courseId, Long parentId);
    List<MaterialFolder> findByCourse_IdAndParentIsNullOrderByCreatedAtAsc(Long courseId);
}
