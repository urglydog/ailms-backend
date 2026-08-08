package com.lms.catalog.service;

import com.lms.catalog.dto.LessonDocumentDto.Res;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.entity.LessonDocument;
import com.lms.catalog.repository.LessonDocumentRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tài liệu đính kèm bài học (Giai đoạn 4, UC35). BR-UPLOAD-01: pdf/docx/pptx/zip/txt,
 * tối đa 50MB/file, 5 file/bài, kiểm định dạng thật bằng magic number (Tika), không tin đuôi file.
 */
@Service
@RequiredArgsConstructor
public class LessonDocumentService {

    /** MIME thật (Tika) -> đuôi file hợp lệ theo BR-UPLOAD-01. */
    private static final Map<String, String> ALLOWED_MIME_TO_EXTENSION = Map.of(
            "application/pdf", "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
            "application/zip", "zip",
            "application/x-zip-compressed", "zip",
            "text/plain", "txt"
    );

    private final LessonDocumentRepository lessonDocumentRepository;
    private final LessonService lessonService;
    private final StorageService storageService;
    private final Tika tika = new Tika();

    @Value("${lms.rules.max-documents-per-lesson}")
    private int maxDocumentsPerLesson;

    @Value("${lms.rules.max-document-size-mb}")
    private long maxDocumentSizeMb;

    @Transactional(readOnly = true)
    public List<Res> list(String instructorEmail, Long lessonId) {
        lessonService.loadOwnedLesson(lessonId, instructorEmail);
        return lessonDocumentRepository.findByLesson_IdOrderByIdAsc(lessonId).stream()
                .map(this::mapToRes)
                .toList();
    }

    /** BR-COURSE-06 — Admin xem tài liệu đính kèm để kiểm duyệt (khóa đang PENDING), không cần sở hữu. */
    @Transactional(readOnly = true)
    public List<Res> listForModeration(Long lessonId) {
        lessonService.loadLessonForModeration(lessonId);
        return lessonDocumentRepository.findByLesson_IdOrderByIdAsc(lessonId).stream()
                .map(this::mapToRes)
                .toList();
    }

    @Transactional
    public Res upload(String instructorEmail, Long lessonId, MultipartFile file) {
        Lesson lesson = lessonService.loadOwnedLesson(lessonId, instructorEmail);

        if (file.isEmpty()) {
            throw new InvalidRequestException("File tài liệu trống");
        }
        long maxBytes = maxDocumentSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("File vượt quá " + maxDocumentSizeMb + "MB");
        }
        long existingCount = lessonDocumentRepository.countByLesson_Id(lessonId);
        if (existingCount >= maxDocumentsPerLesson) {
            throw new BusinessRuleViolationException(
                    "Đã đạt tối đa " + maxDocumentsPerLesson + " tài liệu cho bài học này");
        }

        String detectedMime;
        try (InputStream sniff = file.getInputStream()) {
            detectedMime = tika.detect(sniff);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file: " + e.getMessage());
        }
        String extension = ALLOWED_MIME_TO_EXTENSION.get(detectedMime);
        if (extension == null) {
            throw new InvalidRequestException(
                    "Định dạng file không được hỗ trợ (chỉ nhận pdf/docx/pptx/zip/txt)");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "tai-lieu";
        String sanitized = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = "documents/" + lessonId + "/" + UUID.randomUUID() + "-" + sanitized;

        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được tài liệu lên kho lưu trữ: " + e.getMessage());
        }

        LessonDocument document = new LessonDocument();
        document.setLesson(lesson);
        document.setFileName(originalName);
        document.setFileUrl(url);
        document.setFileType(extension);
        document.setFileSize(file.getSize());
        return mapToRes(lessonDocumentRepository.save(document));
    }

    @Transactional
    public void delete(String instructorEmail, Long documentId) {
        LessonDocument document = lessonDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("LessonDocument", documentId));
        if (!document.getLesson().getChapter().getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên tài liệu này");
        }
        lessonDocumentRepository.delete(document);
        storageService.delete(StorageService.extractKeyFromUrl(document.getFileUrl()));
    }

    private Res mapToRes(LessonDocument document) {
        return new Res(
                document.getId(),
                document.getFileName(),
                document.getFileUrl(),
                document.getFileType(),
                document.getFileSize()
        );
    }
}
