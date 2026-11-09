package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.catalog.entity.Lesson;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Mot tin nhan trong phien hoi dap Socratic Tutor.
 *
 * <p><b>BR-TUTOR-02:</b> moi tin nhan {@code sender = AI} lien quan den kien thuc
 * bai giang <b>BAT BUOC</b> co it nhat mot moc thoi gian trong {@code citedTimestamps}.
 * Frontend bien cac moc nay thanh lien ket nhap duoc de tua Dual Player.
 *
 * <p><b>BR-TUTOR-01:</b> noi dung phan hoi khong duoc dua dap an truc tiep, chi 1-2
 * cau hoi goi mo.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage extends BaseEntity {

    /** USER hoac AI. */
    @Column(name = "sender", nullable = false, length = 10)
    private String sender;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** JSON mang giay, vi du [255, 612]. Bat buoc co voi phan hoi AI (BR-TUTOR-02). */
    @Column(name = "cited_timestamps", columnDefinition = "TEXT")
    private String citedTimestamps;

    /** UC30 mo rong (06/09/2026) — bai hoc THAT SU duoc dung lam ngu canh cho cau tra loi
     * nay, CO THE KHAC bai hoc hoc vien dang mo tren trinh duyet neu ho hoi ro ve 1 bai
     * khac trong cung khoa hoc (xem TutorService.ask, TutorDto.AskReq.currentLessonId).
     * NULL o tin nhan USER — chi tin AI moi can. FE dung gia tri nay de biet cac moc
     * {@link #citedTimestamps} thuoc video bai hoc nao ma tua dung, tranh tua nham vao
     * video dang mo khi no thuoc ve 1 bai hoc khac. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_lesson_id")
    private Lesson contextLesson;

    /** Theo doi chi phi LLM (BR-TUTOR-04). */
    @Column(name = "token_used")
    private Integer tokenUsed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;
}
