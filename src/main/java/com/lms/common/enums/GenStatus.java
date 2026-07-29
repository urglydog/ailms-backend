package com.lms.common.enums;

/**
 * Trạng thái lượt sinh học liệu (BR-MAT-06).
 * Đầu ra LLM sai định dạng -> gọi lại tối đa 2 lần -> vẫn sai thì FAILED.
 * FAILED không được làm gián đoạn pipeline lồng tiếng chính.
 *
 * <p>Dùng bởi: MaterialGeneration</p>
 */
public enum GenStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
