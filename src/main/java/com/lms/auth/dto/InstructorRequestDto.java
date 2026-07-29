package com.lms.auth.dto;

import com.lms.common.enums.RequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class InstructorRequestDto {
    public record CreateReq(
            @NotBlank(message = "Lý do không được để trống")
            String motivation,
            String credentialUrl
    ) {}

    public record UpdateStatusReq(
            @NotNull(message = "Trạng thái duyệt không được để trống")
            RequestStatus status,
            String adminNotes
    ) {}

    public record Res(
            Long id,
            Long userId,
            String motivation,
            String credentialUrl,
            RequestStatus status,
            String rejectReason,
            LocalDateTime createdAt
    ) {}
}
