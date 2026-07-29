package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
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

    /** Theo doi chi phi LLM (BR-TUTOR-04). */
    @Column(name = "token_used")
    private Integer tokenUsed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;
}
