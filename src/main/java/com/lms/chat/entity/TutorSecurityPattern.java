package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 1 pattern nghi vấn cho lớp pre-check heuristic của Socratic Tutor Agent (BR-TUTOR-SEC-06,
 * doc/feat/injection/DacTa_ChongPromptInjection_TutorAgent.md mục 5).
 *
 * <p>Lưu dạng CẤU HÌNH trong DB (không hardcode trong code) để Admin bổ sung/tắt pattern mới khi
 * phát hiện qua log {@link TutorSecurityFlag}, không cần deploy lại. {@link #pattern} là 1 regex
 * Java, khớp trên bản tin nhắn ĐÃ được chuẩn hoá (bỏ dấu tiếng Việt + viết thường) — xem
 * {@code com.lms.chat.service.TutorSecurityService}.
 */
@Entity
@Table(name = "tutor_security_patterns")
@Getter
@Setter
public class TutorSecurityPattern extends BaseEntity {

    @Column(name = "pattern", nullable = false)
    private String pattern;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
