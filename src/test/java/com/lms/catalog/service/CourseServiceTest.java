package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.dto.CourseDto.*;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.CourseInvite;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseInviteRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.storage.StorageService;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra BR-COURSE-01 (điều kiện gửi duyệt), BR-COURSE-03 (xoá mềm/cứng),
 * BR-COURSE-04 (giới hạn 5 lần gửi lại) và BR-ROLE-01 (ownership).
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final String OWNER_EMAIL = "instructor@lms.local";

    @Mock private CourseRepository courseRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private InstructorVerificationRepository instructorVerificationRepository;
    @Mock private CourseInviteRepository courseInviteRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CourseService courseService;

    private Course course;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(OWNER_EMAIL);
        instructor.setFullName("Giảng viên A");

        Category category = new Category();
        category.setId(2L);
        category.setName("Tiếng Anh");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa học mẫu");
        course.setDescription("Mô tả đầy đủ");
        course.setThumbnailUrl("https://example.com/thumb.png");
        course.setInstructor(instructor);
        course.setCategory(category);
        course.setStatus(CourseStatus.DRAFT);
        course.setResubmitCount(0);

        // lenient: test create() mới (chia doanh thu 2 mức) không thao tác khóa id=10L có sẵn.
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chapterRepository.findByCourseIdOrderByDisplayOrderAsc(anyLong())).thenReturn(List.of());
        // BR-VERIFY-01 (19/09/2026, khôi phục) — mặc định coi như ĐÃ xác minh để không ảnh hưởng
        // các test khác vốn kiểm tra BR-COURSE-01/04; có test riêng cho nhánh CHƯA xác minh bên dưới.
        lenient().when(instructorVerificationRepository.existsByUser_Id(anyLong())).thenReturn(true);
    }

    // ── create (chia doanh thu 2 mức, 20/09/2026) ──────────────────────

    @Test
    void create_generatesNonBlankUniqueReferralCode() {
        User instructor = new User();
        instructor.setId(5L);
        instructor.setEmail("moi@lms.local");
        instructor.setHeadline("Giảng viên Tiếng Anh");
        instructor.setBio("Tiểu sử đủ dài ít nhất 20 ký tự để vượt điều kiện.");

        Category category = new Category();
        category.setId(2L);
        category.setName("Tiếng Anh");

        when(userRepository.findByEmail("moi@lms.local")).thenReturn(Optional.of(instructor));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));

        DetailRes result1 = courseService.create("moi@lms.local", new CreateReq("Khóa A", "Mô tả", 2L, "BEGINNER", BigDecimal.ZERO));
        DetailRes result2 = courseService.create("moi@lms.local", new CreateReq("Khóa B", "Mô tả", 2L, "BEGINNER", BigDecimal.ZERO));

        assertThat(result1.referralCode()).isNotBlank();
        assertThat(result2.referralCode()).isNotBlank();
        assertThat(result1.referralCode()).isNotEqualTo(result2.referralCode());
    }

    @Test
    void submitForReview_blocksWhenMissingChaptersAndLessons() {
        when(chapterRepository.countByCourseId(10L)).thenReturn(0L);
        lenient().when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(0L);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void submitForReview_blocksWhenLessonsExistButNoneAreReady() {
        // Đủ số lượng bài học nhưng toàn DRAFT (chưa nạp video) — vẫn phải chặn (Giai đoạn 4, UC34).
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(0L);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void submitForReview_fromDraft_movesToPendingWithoutTouchingResubmitCount() {
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(3L);

        DetailRes result = courseService.submitForReview(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PENDING);
        assertThat(result.resubmitCount()).isZero();
    }

    @Test
    void submitForReview_fromRejected_incrementsResubmitCount() {
        course.setStatus(CourseStatus.REJECTED);
        course.setResubmitCount(3);
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(3L);

        DetailRes result = courseService.submitForReview(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PENDING);
        assertThat(result.resubmitCount()).isEqualTo(4);
    }

    @Test
    void submitForReview_fromRejected_blocksAtFifthResubmit() {
        course.setStatus(CourseStatus.REJECTED);
        course.setResubmitCount(5);
        when(chapterRepository.countByCourseId(10L)).thenReturn(1L);
        when(lessonRepository.countByChapter_CourseIdAndStatus(10L, "READY")).thenReturn(3L);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("5");

        verify(courseRepository, never()).save(any());
    }

    @Test
    void submitForReview_fromPublished_isRejectedAsInvalidTransition() {
        course.setStatus(CourseStatus.PUBLISHED);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submitForReview_blocksWhenInstructorNotVerified() {
        when(instructorVerificationRepository.existsByUser_Id(1L)).thenReturn(false);

        assertThatThrownBy(() -> courseService.submitForReview(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("BR-VERIFY-01");

        verify(courseRepository, never()).save(any());
    }

    @Test
    void uploadThumbnail_validJpegSucceeds() {
        byte[] jpegMagicBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "bia.jpg", "image/jpeg", jpegMagicBytes);
        when(storageService.upload(anyString(), any(), anyLong(), eq("image/jpeg")))
                .thenReturn("https://cdn.example.com/thumbnails/10/x.jpg");

        DetailRes result = courseService.uploadThumbnail(OWNER_EMAIL, 10L, file);

        assertThat(result.thumbnailUrl()).isEqualTo("https://cdn.example.com/thumbnails/10/x.jpg");
    }

    @Test
    void uploadThumbnail_rejectsFileWhoseRealContentIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bia.jpg", "image/jpeg", "%PDF-1.4 gia mao anh".getBytes());

        assertThatThrownBy(() -> courseService.uploadThumbnail(OWNER_EMAIL, 10L, file))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getMineDetail_throwsWhenCallerIsNotOwner() {
        assertThatThrownBy(() -> courseService.getMineDetail("khac@lms.local", 10L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    /**
     * "Gỡ bỏ khóa học" (19/09/2026, sửa lại theo yêu cầu nghiệp vụ) — KHÔNG BAO GIỜ xoá cứng
     * dữ liệu nữa, dù khóa đang DRAFT và chưa có học viên nào — chỉ chuyển sang ARCHIVED, lưu
     * lại {@code previousStatus} để {@link CourseService#reactivate} khôi phục đúng.
     */
    @Test
    void delete_neverHardDeletes_alwaysArchivesAndSavesPreviousStatus() {
        courseService.delete(OWNER_EMAIL, 10L);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        assertThat(course.getPreviousStatus()).isEqualTo(CourseStatus.DRAFT);
        verify(courseRepository).save(course);
        verify(courseRepository, never()).delete(any(Course.class));
    }

    @Test
    void delete_publishedCourse_archivesAndRemembersPublishedAsPreviousStatus() {
        course.setStatus(CourseStatus.PUBLISHED);

        courseService.delete(OWNER_EMAIL, 10L);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        assertThat(course.getPreviousStatus()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void delete_alreadyArchived_throws() {
        course.setStatus(CourseStatus.ARCHIVED);

        assertThatThrownBy(() -> courseService.delete(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void delete_notOwner_throwsAccessDenied() {
        assertThatThrownBy(() -> courseService.delete("khac@lms.local", 10L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void reactivate_archivedDraftCourse_restoresDraftAndClearsPreviousStatus() {
        course.setStatus(CourseStatus.ARCHIVED);
        course.setPreviousStatus(CourseStatus.DRAFT);

        DetailRes result = courseService.reactivate(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.getPreviousStatus()).isNull();
    }

    @Test
    void reactivate_archivedPublishedCourse_restoresPublished() {
        course.setStatus(CourseStatus.ARCHIVED);
        course.setPreviousStatus(CourseStatus.PUBLISHED);

        DetailRes result = courseService.reactivate(OWNER_EMAIL, 10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void reactivate_notArchived_throws() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> courseService.reactivate(OWNER_EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reactivate_notOwner_throwsAccessDenied() {
        course.setStatus(CourseStatus.ARCHIVED);

        assertThatThrownBy(() -> courseService.reactivate("khac@lms.local", 10L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void reject_onlyAllowedFromPending() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> courseService.reject(10L, new RejectReq("Nội dung chưa đạt chất lượng tối thiểu")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reject_fromPending_setsRejectedWithReason() {
        course.setStatus(CourseStatus.PENDING);
        String reason = "Nội dung chưa đạt chất lượng tối thiểu";

        DetailRes result = courseService.reject(10L, new RejectReq(reason));

        assertThat(result.status()).isEqualTo(CourseStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo(reason);
    }

    @Test
    void approve_onlyAllowedFromPending() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> courseService.approve(10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approve_fromPending_movesToPublished() {
        course.setStatus(CourseStatus.PENDING);

        DetailRes result = courseService.approve(10L);

        assertThat(result.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    // ==================== "Đăng ký (Quyền riêng tư)" (19/09/2026) ====================

    @Test
    void updateVisibility_toPrivatePassword_hashesPassword() {
        when(passwordEncoder.encode("bimat123")).thenReturn("hashed-bimat123");

        DetailRes result = courseService.updateVisibility(
                OWNER_EMAIL, 10L, new VisibilityUpdateReq(CourseVisibility.PRIVATE_PASSWORD, "bimat123"));

        assertThat(result.visibility()).isEqualTo(CourseVisibility.PRIVATE_PASSWORD);
        assertThat(result.hasEnrollPassword()).isTrue();
        assertThat(course.getEnrollPasswordHash()).isEqualTo("hashed-bimat123");
    }

    @Test
    void updateVisibility_toPrivatePassword_firstTimeWithoutPassword_throws() {
        assertThatThrownBy(() -> courseService.updateVisibility(
                OWNER_EMAIL, 10L, new VisibilityUpdateReq(CourseVisibility.PRIVATE_PASSWORD, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateVisibility_toPrivatePassword_blankPasswordKeepsExistingHash() {
        course.setEnrollPasswordHash("hash-cu");

        DetailRes result = courseService.updateVisibility(
                OWNER_EMAIL, 10L, new VisibilityUpdateReq(CourseVisibility.PRIVATE_PASSWORD, "  "));

        assertThat(result.hasEnrollPassword()).isTrue();
        assertThat(course.getEnrollPasswordHash()).isEqualTo("hash-cu");
    }

    @Test
    void updateVisibility_toPublic_clearsPasswordHash() {
        course.setEnrollPasswordHash("hash-cu");
        course.setVisibility(CourseVisibility.PRIVATE_PASSWORD);

        DetailRes result = courseService.updateVisibility(
                OWNER_EMAIL, 10L, new VisibilityUpdateReq(CourseVisibility.PUBLIC, null));

        assertThat(result.visibility()).isEqualTo(CourseVisibility.PUBLIC);
        assertThat(result.hasEnrollPassword()).isFalse();
        assertThat(course.getEnrollPasswordHash()).isNull();
    }

    @Test
    void updateVisibility_notOwner_throwsAccessDenied() {
        assertThatThrownBy(() -> courseService.updateVisibility(
                "khac@lms.local", 10L, new VisibilityUpdateReq(CourseVisibility.PRIVATE_INVITE, null)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void addInvite_savesNormalizedLowercaseEmail() {
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, "hocvien@lms.local")).thenReturn(false);

        courseService.addInvite(OWNER_EMAIL, 10L, "  HocVien@LMS.local  ");

        org.mockito.ArgumentCaptor<CourseInvite> captor = org.mockito.ArgumentCaptor.forClass(CourseInvite.class);
        verify(courseInviteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("hocvien@lms.local");
        assertThat(captor.getValue().getCourse()).isEqualTo(course);
    }

    @Test
    void addInvite_alreadyInvited_doesNotSaveDuplicate() {
        when(courseInviteRepository.existsByCourse_IdAndEmail(10L, "hocvien@lms.local")).thenReturn(true);

        courseService.addInvite(OWNER_EMAIL, 10L, "hocvien@lms.local");

        verify(courseInviteRepository, never()).save(any());
    }

    @Test
    void removeInvite_notOwner_throwsAccessDenied() {
        assertThatThrownBy(() -> courseService.removeInvite("khac@lms.local", 10L, "hocvien@lms.local"))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(courseInviteRepository, never()).deleteByCourse_IdAndEmail(anyLong(), anyString());
    }

    @Test
    void listInvites_returnsEmailsOfOwnedCourse() {
        CourseInvite invite = new CourseInvite();
        invite.setCourse(course);
        invite.setEmail("hocvien@lms.local");
        when(courseInviteRepository.findByCourse_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(invite));

        List<String> result = courseService.listInvites(OWNER_EMAIL, 10L);

        assertThat(result).containsExactly("hocvien@lms.local");
    }
}
