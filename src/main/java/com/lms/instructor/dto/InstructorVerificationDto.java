package com.lms.instructor.dto;

import java.time.LocalDateTime;

/** DTO cho {@code /api/v1/instructor/verification/**} (15/09/2026). */
public class InstructorVerificationDto {

    /** Chỉ trả về cho CHÍNH CHỦ tài khoản vừa nộp/đang kiểm tra — xem docblock entity. */
    public record Res(
            Long id,
            String idNumber,
            String idPhotoUrl,
            String addressText,
            Boolean contentOwnershipConfirmed,
            LocalDateTime verifiedAt
    ) {}

    /** {@code verified=false} khi tài khoản chưa nộp — FE dùng để quyết định có chặn nút "Gửi duyệt" hay không. */
    public record StatusRes(Boolean verified) {}
}
