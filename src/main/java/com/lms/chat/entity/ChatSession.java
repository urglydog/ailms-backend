package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Phien hoi dap voi Socratic AI Tutor (UC30), gan voi MOT KHOA HOC (06/09/2026 — truoc day
 * gan voi 1 bai hoc cu the, doi lai vi hoc vien phai thay lich su chat khac nhau moi khi
 * chuyen bai trong cung 1 khoa hoc). 1 hoc vien + 1 khoa hoc dung CHUNG 1 danh sach lich
 * su chat cho MOI bai hoc trong khoa. Bai hoc dang mo hien tai KHONG con co dinh theo
 * session nua — truyen theo TUNG luot hoi (xem {@code TutorDto.AskReq.currentLessonId}),
 * AI tu mac dinh tra loi theo bai do tru khi hoc vien noi ro 1 bai KHAC trong khoa.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

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
