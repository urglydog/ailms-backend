package com.lms.common.enums;

/**
 * Mức độ khó, ánh xạ thang Bloom (BR-MAT-05).
 * EASY: ghi nhớ / định nghĩa
 * MEDIUM: hiểu / áp dụng
 * HARD: phân tích / so sánh
 * Truyền vào chỉ dẫn prompt gửi LLM. Mindmap KHÔNG có tùy chọn này.
 *
 * <p>Dùng bởi: MaterialGeneration</p>
 */
public enum DifficultyLevel {
    EASY,
    MEDIUM,
    HARD
}
