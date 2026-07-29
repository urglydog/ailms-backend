package com.lms.common.enums;

/**
 * Mức số lượng học liệu (BR-MAT-05), áp cho WHOLE_COURSE.
 * FEWER: 10-15 câu Quiz / 20-30 thẻ Flashcard
 * STANDARD: 20-30 câu / 40-60 thẻ (mặc định)
 * MORE: 30-40 câu / 60-80 thẻ
 * Phạm vi hẹp hơn thì tính tỷ lệ theo số bài, sàn 5 câu / 10 thẻ.
 * Mindmap KHÔNG có tùy chọn này.
 *
 * <p>Dùng bởi: MaterialGeneration</p>
 */
public enum QuantityLevel {
    FEWER,
    STANDARD,
    MORE
}
