package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tài liệu đính kèm bài học (UC35).
 *
 * <p>BR-UPLOAD-01: chỉ nhận pdf/docx/pptx/zip/txt, tối đa <b>50 MB mỗi file</b> và
 * <b>5 file mỗi bài học</b>. Phải kiểm định dạng thực tế bằng <b>magic number</b>,
 * không tin phần mở rộng tên file.
 */
@Entity
@Table(name = "lesson_documents")
@Getter
@Setter
public class LessonDocument extends BaseEntity {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /** pdf / docx / pptx / zip / txt (BR-UPLOAD-01). */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    /** Tính bằng byte. Giới hạn 50 MB kiểm ở tầng service (BR-UPLOAD-01). */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
