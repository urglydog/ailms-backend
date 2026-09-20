package com.lms.instructor.dto;

import java.time.LocalDateTime;

public class InstructorVerificationDto {

    public record Res(
            Long id,
            String idNumber,
            String addressText,
            Boolean contentOwnershipConfirmed,
            LocalDateTime verifiedAt
    ) {}

    /** FE dùng để quyết định có chặn nút "Gửi duyệt" khóa học đầu tiên hay không (BR-VERIFY-01). */
    public record StatusRes(Boolean verified) {}

    public record SubmitReq(String idNumber, String addressText, Boolean contentOwnershipConfirmed) {}
}
