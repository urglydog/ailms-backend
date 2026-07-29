package com.lms.common.enums;

/**
 * Trạng thái job AI (BR-CHUNK-04, BR-DUB-10).
 * FAILED được retry tối đa 3 lần với exponential backoff.
 * SKIPPED (video không có lời thoại) TUYỆT ĐỐI KHÔNG retry.
 *
 * <p>Dùng bởi: AiJob, AiJobChunk</p>
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}
