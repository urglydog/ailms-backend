package com.lms.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho {@code /api/v1/courses/{courseId}/tutor/**} (UC30). */
public class TutorDto {

    public record AskReq(
            @NotBlank @Size(max = 2000) String question,
            /** Bỏ trống ở tin đầu tiên — {@link com.lms.chat.service.TutorService} tự tạo
             * {@code ChatSession} mới hoặc tái sử dụng phiên gần nhất của (học viên, khóa học). */
            Long sessionId,
            /** UC30 mở rộng (06/09/2026) — bài học học viên ĐANG MỞ trên trình duyệt lúc hỏi câu
             * này, dùng làm ngữ cảnh MẶC ĐỊNH cho AI Worker: không nói rõ bài nào thì trả lời
             * theo đúng bài đang mở; nói rõ tên/số 1 bài KHÁC trong cùng khóa học thì AI tự đổi
             * ngữ cảnh sang bài đó (xem {@code TutorService.callAiWorker}, AI Worker tự phân
             * loại bằng Gemini dựa trên danh sách bài học của khóa). */
            @NotNull Long currentLessonId,
            /** UC30 mở rộng — tệp đính kèm (ảnh/tài liệu/mã nguồn), tối đa
             * {@code lms.rules.max-tutor-attachments-per-turn} tệp/lượt hỏi. */
            List<AttachmentReq> attachments
    ) {}

    /** UC30 mở rộng — 1 tệp học viên gửi kèm câu hỏi. `mimeType` client gửi lên CHỈ để tham
     * khảo — server tự dò lại bằng magic number (Tika), không tin giá trị này. */
    public record AttachmentReq(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank String dataBase64
    ) {}

    public record AskRes(
            Long sessionId,
            String answer,
            /** Giây, BR-TUTOR-02 — luôn có ≥1 phần tử khi câu trả lời liên quan bài giảng. */
            List<Integer> citedTimestamps,
            Integer tokenUsed,
            /** UC30 mở rộng (06/09/2026) — bài học THẬT SỰ được dùng làm ngữ cảnh cho câu trả
             * lời này (có thể khác {@code currentLessonId} đã gửi lên, nếu học viên hỏi rõ về
             * 1 bài khác) — FE dùng để biết {@code citedTimestamps} thuộc video bài học nào. */
            Long contextLessonId
    ) {}

    /** UC30 mở rộng — 1 dòng trong danh sách "lịch sử trò chuyện" kiểu ChatGPT. */
    public record SessionRes(
            Long id,
            /** `title` do học viên đặt hoặc AI tự gợi ý; rút gọn câu hỏi đầu nếu chưa có cái nào;
             * "Cuộc trò chuyện mới" nếu chưa có tin nào. */
            String title,
            /** Thời điểm tin nhắn GẦN NHẤT của phiên (không phải lúc TẠO phiên) — quyết định thứ
             * tự hiển thị trong nhóm "chưa ghim", giống cách ChatGPT/Gemini đẩy cuộc chat vừa
             * nhắn lên đầu danh sách. */
            LocalDateTime lastActivityAt,
            boolean isPinned
    ) {}

    /** UC30 mở rộng — 1 tin nhắn khi phục hồi lại 1 phiên chat cũ. */
    public record MessageRes(
            Long id,
            String sender,
            String content,
            List<Integer> citedTimestamps,
            List<AttachmentRes> attachments,
            /** NULL ở tin nhắn USER — xem giải thích ở {@link AskRes#contextLessonId()}. */
            Long contextLessonId
    ) {}

    /** UC30 mở rộng — tệp đính kèm khi hiển thị lại lịch sử. `fileUrl` CHỈ dùng để xem inline
     * trong khung chat (ảnh render trực tiếp) — KHÔNG lộ ra như 1 link "tải xuống" ở FE, đỡ tốn
     * hạn mức băng thông B2 cho việc tải lại nhiều lần cùng 1 tệp cũ. */
    public record AttachmentRes(
            Long id,
            String fileName,
            String fileUrl,
            String mimeType
    ) {}

    /** UC30 mở rộng — đổi tên cuộc trò chuyện thủ công. */
    public record RenameSessionReq(
            @NotBlank @Size(max = 255) String title
    ) {}

    /** UC30 mở rộng — ghim/bỏ ghim. */
    public record PinSessionReq(
            boolean pinned
    ) {}
}
