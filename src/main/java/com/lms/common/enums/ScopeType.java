package com.lms.common.enums;

/**
 * Phạm vi nội dung dùng để sinh học liệu (BR-MAT-02).
 * CHAPTER thì scopeRefId trỏ tới id của Chapter.
 * COMPLETED_LESSONS chỉ lấy bài đã COMPLETED tại thời điểm yêu cầu.
 *
 * <p>Dùng bởi: MaterialGeneration</p>
 */
public enum ScopeType {
    WHOLE_COURSE,
    CHAPTER,
    COMPLETED_LESSONS,
    CUSTOM_LESSONS
}
