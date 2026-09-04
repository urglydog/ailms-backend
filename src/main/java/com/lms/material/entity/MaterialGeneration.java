package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.DifficultyLevel;
import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import com.lms.common.enums.QuantityLevel;
import com.lms.common.enums.ScopeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot luot sinh hoc lieu theo yeu cau hoc vien (UC24, UC25, UC26).
 *
 * <p><b>BR-MAT-01 - hai diem tuyet doi khong duoc lam sai:</b>
 * <ul>
 *   <li>Hoc lieu o <b>cap khoa hoc</b>, gan {@code Course} - KHONG co {@code lesson_id}.</li>
 *   <li>Moi bo thuoc ve <b>mot hoc vien cu the</b>, khong chia se cho ai khac, ke ca
 *       khi trung hoan toan loai/ngon ngu/pham vi.</li>
 * </ul>
 *
 * <p><b>BR-MAT-07:</b> moi luot tao sinh ban ghi moi voi {@code versionNo} tang dan,
 * <b>khong ghi de</b> bo cu. Gioi han 10 bo moi (hoc vien, khoa hoc) - kiem o tang
 * service vi MySQL khong dien dat duoc rang buoc nay bang CHECK constraint.
 */
@Entity
@Table(name = "material_generations")
@Getter
@Setter
public class MaterialGeneration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    /** Hoc vien tu chon, doc lap voi ngon ngu audio da co (BR-MAT-01). */
    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private ScopeType scopeType;

    /** Id cua Chapter khi scopeType = CHAPTER, nguoc lai NULL. */
    @Column(name = "scope_ref_id")
    private Long scopeRefId;

    /** Danh sach cac id bai hoc tuy chon khi scopeType = CUSTOM_LESSONS. (chuoi phan cach boi dau phay) */
    @Column(name = "custom_lesson_ids", columnDefinition = "TEXT")
    private String customLessonIds;

    /** Chi co gia tri khi materialType thuoc {QUIZ, FLASHCARD} (BR-MAT-05). */
    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_level", length = 20)
    private QuantityLevel quantityLevel;

    /** Chi co gia tri khi materialType thuoc {QUIZ, FLASHCARD} (BR-MAT-05). */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 20)
    private DifficultyLevel difficultyLevel;

    /** Tang dan, khong ghi de bo cu (BR-MAT-07). */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "title", length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GenStatus status = GenStatus.PENDING;

    @Column(name = "celery_task_id", length = 100)
    private String celeryTaskId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Nguoi yeu cau tao - bo hoc lieu chi thuoc ve nguoi nay. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Hoc lieu o CAP KHOA HOC, khong phai cap bai hoc (BR-MAT-01). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** BR-MAT-01: Cho phép người tạo hoặc Giảng viên sở hữu khóa học được xem/sử dụng học liệu. */
    public boolean isReusableBy(User other) {
        if (user == null || other == null) return false;
        if (user.getId() != null && user.getId().equals(other.getId())) return true;
        if (course != null && course.getInstructor() != null && course.getInstructor().getId() != null && course.getInstructor().getId().equals(other.getId())) return true;
        return false;
    }
}
