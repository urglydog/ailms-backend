package com.lms.enrollment.security;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Kiểm tra {@link EnrollmentSecurity} — đặc biệt nhánh mới (19/09/2026): Giảng viên sở hữu
 * khóa học luôn truy cập được khóa/bài học của MÌNH, bất kể trạng thái/ghi danh, để nút "Xem
 * trước > Với tư cách là Giảng viên" ở trang chỉnh sửa khóa học hoạt động đúng. */
@ExtendWith(MockitoExtension.class)
class EnrollmentSecurityTest {

    private static final String INSTRUCTOR_EMAIL = "giangvien@lms.local";
    private static final String STUDENT_EMAIL = "hocvien@lms.local";
    private static final String STRANGER_EMAIL = "nguoila@lms.local";

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LessonRepository lessonRepository;

    @InjectMocks
    private EnrollmentSecurity enrollmentSecurity;

    private Course course;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail(INSTRUCTOR_EMAIL);

        course = new Course();
        course.setId(10L);
        course.setInstructor(instructor);
        course.setStatus(CourseStatus.DRAFT);

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(100L);
        lesson.setChapter(chapter);
        lesson.setIsPreview(false);
    }

    @Test
    void canAccessCourse_owningInstructor_trueEvenWhileDraft() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThat(enrollmentSecurity.canAccessCourse(INSTRUCTOR_EMAIL, 10L)).isTrue();
    }

    @Test
    void canAccessCourse_strangerOnDraftCourse_false() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STRANGER_EMAIL, 10L)).thenReturn(false);

        assertThat(enrollmentSecurity.canAccessCourse(STRANGER_EMAIL, 10L)).isFalse();
    }

    @Test
    void canAccessCourse_enrolledStudent_trueEvenIfArchived() {
        course.setStatus(CourseStatus.ARCHIVED);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STUDENT_EMAIL, 10L)).thenReturn(true);

        assertThat(enrollmentSecurity.canAccessCourse(STUDENT_EMAIL, 10L)).isTrue();
    }

    @Test
    void canAccessLesson_owningInstructor_trueForNonPreviewNonPublished() {
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));

        assertThat(enrollmentSecurity.canAccessLesson(INSTRUCTOR_EMAIL, 100L, true)).isTrue();
    }

    @Test
    void canAccessLesson_strangerOnUnpublishedNonPreview_false() {
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        lenient().when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STRANGER_EMAIL, 10L)).thenReturn(false);

        assertThat(enrollmentSecurity.canAccessLesson(STRANGER_EMAIL, 100L, true)).isFalse();
    }

    @Test
    void canAccessLesson_strangerOnPublishedPreview_true() {
        course.setStatus(CourseStatus.PUBLISHED);
        lesson.setIsPreview(true);
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STRANGER_EMAIL, 10L)).thenReturn(false);

        assertThat(enrollmentSecurity.canAccessLesson(STRANGER_EMAIL, 100L, true)).isTrue();
    }
}
