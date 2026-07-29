package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Lesson;
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
}
