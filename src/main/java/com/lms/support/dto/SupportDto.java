package com.lms.support.dto;

import com.lms.common.enums.SupportTicketCategory;
import com.lms.common.enums.SupportTicketStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public class SupportDto {

    public record CreateReq(
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull SupportTicketCategory category,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(min = 10, max = 3000) String message
    ) {}

    /** Trả cho người gửi sau khi tạo — chỉ cần mã để họ đối chiếu với email xác nhận. */
    public record CreateRes(String code) {}

    public record AttachmentRes(Long id, String fileUrl, String fileType) {}

    public record AdminRes(
            Long id,
            String code,
            String email,
            /** Id user nếu email khớp tài khoản — để Admin bấm sang trang chi tiết user gửi link reset. */
            Long userId,
            /** Tên dùng trong lời chào email: họ tên nếu có tài khoản, không thì phần trước @ của email. */
            String senderName,
            SupportTicketCategory category,
            String subject,
            String message,
            SupportTicketStatus status,
            String adminReply,
            List<String> adminSteps,
            String resolvedByEmail,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            List<AttachmentRes> attachments
    ) {}

    public record UpdateReq(
            @NotNull SupportTicketStatus status,
            @Size(max = 3000) String reply,
            /** Các bước hướng dẫn hiển thị dạng danh sách đánh số trong email, tối đa 10 bước, mỗi bước tối đa 500 ký tự. */
            @Size(max = 10) List<@Size(max = 500) String> steps
    ) {}

    public record OpenCountRes(long open) {}
}
