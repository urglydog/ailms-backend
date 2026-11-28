package com.lms.note.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.security.EnrollmentSecurity;
import com.lms.note.dto.LessonNoteDto.CreateReq;
import com.lms.note.dto.LessonNoteDto.NoteRes;
import com.lms.note.dto.LessonNoteDto.UpdateReq;
import com.lms.note.entity.LessonNote;
import com.lms.note.repository.LessonNoteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi chú gắn mốc thời gian video (26/11/2026, mở rộng ngoài đặc tả gốc). Chỉ người có quyền
 * học bài (đã ghi danh, hoặc giảng viên sở hữu — cùng quy tắc {@link EnrollmentSecurity}) mới
 * tạo được; đọc/sửa/xoá chỉ trên ghi chú CỦA CHÍNH MÌNH — ghi chú người khác trả 404 (không lộ
 * sự tồn tại).
 */
@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final LessonNoteRepository noteRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final EnrollmentSecurity enrollmentSecurity;

    /** {@code scope} = "course" → mọi bài trong khoá của bài này; còn lại → chỉ bài này. */
    @Transactional(readOnly = true)
    public List<NoteRes> list(String email, Long lessonId, String scope) {
        User user = requireUser(email);
        Lesson lesson = requireLessonAccess(email, lessonId);
        List<LessonNote> notes = "course".equalsIgnoreCase(scope)
                ? noteRepository.findByUser_IdAndCourse_IdOrderByLesson_IdAscTimestampSecAscIdAsc(
                        user.getId(), lesson.getChapter().getCourse().getId())
                : noteRepository.findByUser_IdAndLesson_IdOrderByTimestampSecAscIdAsc(user.getId(), lessonId);
        return notes.stream().map(LessonNoteService::toRes).toList();
    }

    @Transactional
    public NoteRes create(String email, Long lessonId, CreateReq req) {
        User user = requireUser(email);
        Lesson lesson = requireLessonAccess(email, lessonId);
        Course course = lesson.getChapter().getCourse();
        LessonNote note = new LessonNote();
        note.setUser(user);
        note.setLesson(lesson);
        note.setCourse(course);
        note.setTimestampSec(req.timestampSec());
        note.setContent(req.content().trim());
        return toRes(noteRepository.save(note));
    }

    @Transactional
    public NoteRes update(String email, Long noteId, UpdateReq req) {
        LessonNote note = requireOwned(email, noteId);
        note.setContent(req.content().trim());
        if (req.timestampSec() != null) {
            note.setTimestampSec(req.timestampSec());
        }
        return toRes(noteRepository.save(note));
    }

    @Transactional
    public void delete(String email, Long noteId) {
        noteRepository.delete(requireOwned(email, noteId));
    }

    private LessonNote requireOwned(String email, Long noteId) {
        User user = requireUser(email);
        LessonNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("LessonNote", noteId));
        if (!note.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("LessonNote", noteId);
        }
        return note;
    }

    private Lesson requireLessonAccess(String email, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (!enrollmentSecurity.canAccessLesson(email, lessonId, false)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }
        return lesson;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    static NoteRes toRes(LessonNote n) {
        return new NoteRes(n.getId(), n.getLesson().getId(), n.getLesson().getTitle(),
                n.getCourse().getId(), n.getTimestampSec(), n.getContent(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
