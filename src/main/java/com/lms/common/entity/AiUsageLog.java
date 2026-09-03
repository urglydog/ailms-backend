package com.lms.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Lưu vết tiêu thụ Token LLM mỗi khi gọi API (Cong viec 5).
 *
 * <p>Admin sử dụng bảng này để theo dõi chi phí API theo người dùng
 * và tính năng, phát hiện lạm dụng để kích hoạt cờ {@code isAiLocked}
 * trên {@link com.lms.auth.entity.User}.
 *
 * <p><b>featureType</b> ghi nhận loại tính năng gọi LLM:
 * QUIZ, FLASHCARD, MINDMAP, TUTOR, DISCOVERY, DUBBING.
 */
@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_user", columnList = "user_id"),
        @Index(name = "idx_ai_usage_feature", columnList = "feature_type"),
        @Index(name = "idx_ai_usage_created", columnList = "created_at")
})
@Getter
@Setter
public class AiUsageLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** QUIZ, FLASHCARD, MINDMAP, TUTOR, DISCOVERY, DUBBING */
    @Column(name = "feature_type", nullable = false, length = 50)
    private String featureType;

    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens = 0;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens = 0;

    /** Chi phí ước lượng (USD) dựa trên số token tiêu thụ. */
    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;
}
