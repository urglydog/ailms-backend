package com.lms.support.dto;

import com.lms.common.enums.SupportTicketCategory;
import com.lms.common.enums.SupportTicketStatus;
import jakarta.validation.Valid;
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
            /** Bản phẳng (text) của phản hồi gần nhất — hiển thị nhanh, cũng dùng cho ticket cũ chưa có {@link #adminContent}. */
            String adminReply,
            /** Nội dung email phản hồi gần nhất ở dạng có cấu trúc (placeholder chưa thay), null nếu chưa phản hồi kiểu mới. */
            EmailContent adminContent,
            String resolvedByEmail,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            List<AttachmentRes> attachments
    ) {}

    /** 1 mục trong email phản hồi: tiêu đề tùy ý + (đoạn văn hoặc danh sách đánh số). */
    public record Section(
            @Size(max = 150) String title,
            @NotNull SectionType type,
            @Size(max = 3000) String text,
            @Size(max = 10) List<@Size(max = 500) String> items
    ) {}

    public enum SectionType { TEXT, LIST }

    /**
     * Toàn bộ phần nội dung email phản hồi Admin tự soạn. Hỗ trợ placeholder {@code {name}}, {@code {code}},
     * {@code {subject}}, {@code {category}} trong mọi chuỗi. Trường nào null/rỗng thì dùng giá trị mặc định phía BE.
     */
    public record EmailContent(
            @Size(max = 200) String greeting,
            @Size(max = 1000) String intro,
            @Size(max = 8) List<@Valid Section> sections,
            @Size(max = 100) String closing,
            @Size(max = 100) String signatureName,
            @Size(max = 100) String signatureTeam,
            /** Có đính kèm khung "Nội dung yêu cầu ban đầu" trong email hay không (mặc định có). */
            Boolean includeOriginal
    ) {}

    public record UpdateReq(
            @NotNull SupportTicketStatus status,
            @Valid EmailContent content
    ) {}

    public record OpenCountRes(long open) {}
}
