package com.lms.communication.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.communication.dto.AssignmentDto.CreateReq;
import com.lms.communication.dto.AssignmentDto.Res;
import com.lms.communication.dto.AssignmentDto.StudentAssignmentRes;
import com.lms.communication.dto.AssignmentDto.SubmissionRes;
import com.lms.communication.entity.AssignmentSubmission;
import com.lms.communication.entity.CourseAssignment;
import com.lms.communication.repository.AssignmentSubmissionRepository;
import com.lms.communication.repository.CourseAssignmentRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Giao tiếp > Bài tập" (19/09/2026) — kiểm tra quyền sở hữu bài học/bài tập, điều kiện ghi
 * danh trước khi nộp bài, và không cho nộp lại bài đã chấm điểm. */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final String INSTRUCTOR_EMAIL = "giangvien@lms.local";
    private static final String STUDENT_EMAIL = "hocvien@lms.local";

    @Mock private CourseAssignmentRepository assignmentRepository;
    @Mock private AssignmentSubmissionRepository submissionRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private StorageService storageService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AssignmentService assignmentService;

    private User instructor;
    private User student;
    private Course course;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(INSTRUCTOR_EMAIL);

        student = new User();
        student.setId(2L);
        student.setEmail(STUDENT_EMAIL);
        student.setFullName("Học viên A");

        course = new Course();
        course.setId(10L);
        course.setTitle("Khóa Java");
        course.setInstructor(instructor);

        Chapter chapter = new Chapter();
        chapter.setCourse(course);

        lesson = new Lesson();
        lesson.setId(100L);
        lesson.setTitle("Bài 1");
        lesson.setChapter(chapter);
    }

    @Test
    void createAssignment_notOwner_throwsAccessDenied() {
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> assignmentService.createAssignment(
                INSTRUCTOR_EMAIL, 100L, new CreateReq("Bài tập 1", "Làm bài", null, 10)))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void createAssignment_blankTitle_throwsInvalidRequest() {
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> assignmentService.createAssignment(
                INSTRUCTOR_EMAIL, 100L, new CreateReq(" ", "Làm bài", null, 10)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createAssignment_success_returnsRes() {
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            CourseAssignment a = inv.getArgument(0);
            a.setId(200L);
            return a;
        });

        Res res = assignmentService.createAssignment(
                INSTRUCTOR_EMAIL, 100L, new CreateReq("Bài tập 1", "Làm bài", null, 10));

        assertThat(res.id()).isEqualTo(200L);
        assertThat(res.title()).isEqualTo("Bài tập 1");
        assertThat(res.courseTitle()).isEqualTo("Khóa Java");
    }

    @Test
    void submit_notEnrolled_throwsAccessDenied() {
        CourseAssignment assignment = assignmentOf();
        when(assignmentRepository.findById(200L)).thenReturn(Optional.of(assignment));
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> assignmentService.submit(STUDENT_EMAIL, 200L, "Bài làm của em", null))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void submit_emptyContentAndNoFile_throwsInvalidRequest() {
        CourseAssignment assignment = assignmentOf();
        when(assignmentRepository.findById(200L)).thenReturn(Optional.of(assignment));
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> assignmentService.submit(STUDENT_EMAIL, 200L, "  ", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void submit_alreadyGraded_throwsBusinessRuleViolation() {
        CourseAssignment assignment = assignmentOf();
        AssignmentSubmission existing = new AssignmentSubmission();
        existing.setScore(9);
        when(assignmentRepository.findById(200L)).thenReturn(Optional.of(assignment));
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(true);
        when(submissionRepository.findByAssignment_IdAndStudent_Id(200L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> assignmentService.submit(STUDENT_EMAIL, 200L, "Nộp lại", null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submit_textOnly_success() {
        CourseAssignment assignment = assignmentOf();
        when(assignmentRepository.findById(200L)).thenReturn(Optional.of(assignment));
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(2L, 10L)).thenReturn(true);
        when(submissionRepository.findByAssignment_IdAndStudent_Id(200L, 2L)).thenReturn(Optional.empty());
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmissionRes res = assignmentService.submit(STUDENT_EMAIL, 200L, "Bài làm của em", null);

        assertThat(res.textContent()).isEqualTo("Bài làm của em");
        assertThat(res.studentName()).isEqualTo("Học viên A");
    }

    @Test
    void gradeSubmission_notOwner_throwsAccessDenied() {
        CourseAssignment assignment = assignmentOf();
        User otherInstructor = new User();
        otherInstructor.setEmail("khac@lms.local");
        course.setInstructor(otherInstructor);
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        when(submissionRepository.findById(500L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> assignmentService.gradeSubmission(INSTRUCTOR_EMAIL, 500L, 8, "Tốt"))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void gradeSubmission_success_notifiesStudent() {
        CourseAssignment assignment = assignmentOf();
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        when(submissionRepository.findById(500L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmissionRes res = assignmentService.gradeSubmission(INSTRUCTOR_EMAIL, 500L, 8, "Tốt lắm");

        assertThat(res.score()).isEqualTo(8);
        verify(notificationService).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void listForStudent_notEnrolled_throwsAccessDenied() {
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STUDENT_EMAIL, 10L)).thenReturn(false);

        assertThatThrownBy(() -> assignmentService.listForStudent(STUDENT_EMAIL, 100L))
                .isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    void listForStudent_enrolled_includesOwnSubmissionOnly() {
        CourseAssignment assignment = assignmentOf();
        when(lessonRepository.findById(100L)).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUser_EmailAndCourse_Id(STUDENT_EMAIL, 10L)).thenReturn(true);
        when(userRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(assignmentRepository.findByLesson_IdOrderByIdAsc(100L)).thenReturn(List.of(assignment));
        lenient().when(submissionRepository.findByAssignment_IdAndStudent_Id(200L, 2L)).thenReturn(Optional.empty());

        List<StudentAssignmentRes> result = assignmentService.listForStudent(STUDENT_EMAIL, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mySubmission()).isNull();
    }

    private CourseAssignment assignmentOf() {
        CourseAssignment a = new CourseAssignment();
        a.setId(200L);
        a.setTitle("Bài tập 1");
        a.setLesson(lesson);
        a.setMaxScore(10);
        return a;
    }
}
