package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Lesson;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Phien hoi dap voi Socratic AI Tutor (UC30), gan voi mot bai hoc cu the.
 *
 * <p><b>Chi dung cho Socratic Tutor.</b> Course Discovery Agent (UC49) la
 * <b>stateless</b> - khong luu lich su hoi thoai o dau (BR-DISCOVERY-01), nen khong
 * dung entity nay.
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
public class ChatSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Phien hoi dap luon gan voi mot bai hoc cu the de gioi han pham vi RAG. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /** UC30 mo rong — NULL = chua dat (dung ten AI tu goi y sau luot hoi dau, hoac rut gon
     * cau hoi dau neu AI Worker loi); hoc vien tu doi ten thi ghi de vinh vien, khong bao
     * gio bi AI ghi de lai. */
    @Column(name = "title", length = 255)
    private String title;

    /** UC30 mo rong — ghim len dau danh sach lich su (toi da 5, xem TutorService). */
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    /** Moc ghim gan nhat — sap xep cac muc da ghim theo thu tu ghim, khong phai thu tu tao. */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;
}
