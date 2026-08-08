package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.dto.LessonDocumentDto.Res;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.entity.LessonDocument;
import com.lms.catalog.repository.LessonDocumentRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-UPLOAD-01: pdf/docx/pptx/zip/txt, ≤50MB/file, ≤5 file/bài, kiểm magic number thật
 * (dùng Tika thật, không mock — bản thân việc mock Tika sẽ vô hiệu hoá đúng điều cần kiểm chứng).
 */
@ExtendWith(MockitoExtension.class)
class LessonDocumentServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private LessonDocumentRepository lessonDocumentRepository;
    @Mock private LessonService lessonService;
    @Mock private com.lms.common.storage.StorageService storageService;

    @InjectMocks
    private LessonDocumentService lessonDocumentService;

    private Lesson lesson;

    @BeforeEach
    void setUp() {
        lesson = new Lesson();
        lesson.setId(30L);
        ReflectionTestUtils.setField(lessonDocumentService, "maxDocumentsPerLesson", 5);
        ReflectionTestUtils.setField(lessonDocumentService, "maxDocumentSizeMb", 50L);
    }

    @Test
    void upload_validPdfSucceeds() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "giao-trinh.pdf", "application/pdf", "%PDF-1.4\n1 0 obj".getBytes());
        lenient().when(lessonService.loadOwnedLesson(30L, OWNER_EMAIL)).thenReturn(lesson);
        when(lessonDocumentRepository.countByLesson_Id(30L)).thenReturn(0L);
        when(storageService.upload(anyString(), any(), anyLong(), anyString()))
                .thenReturn("https://cdn.example.com/documents/30/x-giao-trinh.pdf");
        when(lessonDocumentRepository.save(any(LessonDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Res result = lessonDocumentService.upload(OWNER_EMAIL, 30L, file);

        assertThat(result.fileType()).isEqualTo("pdf");
        assertThat(result.fileName()).isEqualTo("giao-trinh.pdf");
        assertThat(result.fileUrl()).isEqualTo("https://cdn.example.com/documents/30/x-giao-trinh.pdf");
    }

    /**
     * Mô phỏng tấn công đổi tên file (ví dụ ".exe" -> ".pdf"): dùng ảnh JPEG thật (magic number
     * FF D8 FF được mọi phiên bản Tika nhận diện ổn định) đặt tên/khai content-type là PDF — phải
     * bị chặn vì service tin nội dung thật, không tin đuôi file hay content-type client khai báo.
     */
    @Test
    void upload_rejectsFileWhoseRealContentIsNotAnAllowedType() {
        byte[] jpegMagicBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile(
                "file", "khong-phai-anh.pdf", "application/pdf", jpegMagicBytes);
        lenient().when(lessonService.loadOwnedLesson(30L, OWNER_EMAIL)).thenReturn(lesson);
        when(lessonDocumentRepository.countByLesson_Id(30L)).thenReturn(0L);

        assertThatThrownBy(() -> lessonDocumentService.upload(OWNER_EMAIL, 30L, file))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void upload_rejectsWhenAlreadyAtMaxDocumentCount() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "them-mot-file.pdf", "application/pdf", "%PDF-1.4".getBytes());
        lenient().when(lessonService.loadOwnedLesson(30L, OWNER_EMAIL)).thenReturn(lesson);
        when(lessonDocumentRepository.countByLesson_Id(30L)).thenReturn(5L);

        assertThatThrownBy(() -> lessonDocumentService.upload(OWNER_EMAIL, 30L, file))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void upload_rejectsWhenFileExceedsSizeLimit() {
        ReflectionTestUtils.setField(lessonDocumentService, "maxDocumentSizeMb", 0L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "qua-lon.pdf", "application/pdf", "%PDF-1.4".getBytes());
        lenient().when(lessonService.loadOwnedLesson(30L, OWNER_EMAIL)).thenReturn(lesson);

        assertThatThrownBy(() -> lessonDocumentService.upload(OWNER_EMAIL, 30L, file))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void delete_removesRecordAndCallsStorageDelete() {
        LessonDocument document = documentOwnedBy(OWNER_EMAIL);
        when(lessonDocumentRepository.findById(99L)).thenReturn(Optional.of(document));

        lessonDocumentService.delete(OWNER_EMAIL, 99L);

        verify(lessonDocumentRepository).delete(document);
        verify(storageService).delete(anyString());
    }

    @Test
    void delete_throwsWhenCallerIsNotOwner() {
        LessonDocument document = documentOwnedBy(OWNER_EMAIL);
        when(lessonDocumentRepository.findById(99L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> lessonDocumentService.delete("khac@lms.local", 99L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void listForModeration_returnsDocumentsWhenLessonUnlockedForModeration() {
        LessonDocument doc = new LessonDocument();
        doc.setId(1L);
        doc.setFileName("a.pdf");
        doc.setFileUrl("https://cdn.example.com/documents/30/a.pdf");
        doc.setFileType("pdf");
        doc.setFileSize(100L);
        when(lessonService.loadLessonForModeration(30L)).thenReturn(lesson);
        when(lessonDocumentRepository.findByLesson_IdOrderByIdAsc(30L)).thenReturn(List.of(doc));

        List<Res> result = lessonDocumentService.listForModeration(30L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileName()).isEqualTo("a.pdf");
    }

    @Test
    void listForModeration_propagatesAccessDeniedWhenCourseNotPending() {
        when(lessonService.loadLessonForModeration(30L))
                .thenThrow(new AccessDeniedDomainException("Chỉ được xem nội dung bài học của khóa học đang chờ duyệt"));

        assertThatThrownBy(() -> lessonDocumentService.listForModeration(30L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    private LessonDocument documentOwnedBy(String instructorEmail) {
        User instructor = new User();
        instructor.setEmail(instructorEmail);
        Course course = new Course();
        course.setInstructor(instructor);
        Chapter chapter = new Chapter();
        chapter.setCourse(course);
        Lesson owningLesson = new Lesson();
        owningLesson.setChapter(chapter);

        LessonDocument document = new LessonDocument();
        document.setId(99L);
        document.setLesson(owningLesson);
        document.setFileUrl("https://cdn.example.com/documents/30/abc-file.pdf");
        return document;
    }
}
