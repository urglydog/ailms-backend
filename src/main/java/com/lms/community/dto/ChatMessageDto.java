package com.lms.community.dto;

/**
 * (20/09/2026, sửa lỗi) — trước đây field {@code id} bị dùng LẪN LỘN 2 nghĩa: id tin nhắn (khi
 * đọc lịch sử qua {@code GET .../chats}) và id NGƯỜI GỬI (khi client publish lên WebSocket) —
 * khiến nhiều tin nhắn GỐC do CÙNG 1 người gửi trong 1 phiên live có {@code id} trùng nhau (đều
 * bằng userId của họ), làm hỏng việc gom nhóm câu trả lời theo {@code parentId}. Giờ tách rõ:
 * {@code id} LUÔN là id thật của CHÍNH tin nhắn này (do server gán lúc lưu), {@code senderId} là
 * id người gửi. Thêm {@code isInstructor} (server tính, so với giảng viên sở hữu khóa học) để FE
 * hiển thị nhãn "Giảng viên" — xem {@code components/community/LiveChatPanel.tsx}.
 */
public record ChatMessageDto(
        String id,
        String senderId,
        String senderName,
        /** (20/09/2026, tính năng mới) — hiển thị avatar người gửi trong khung Hỏi đáp. */
        String senderAvatarUrl,
        String content,
        String timestamp,
        String parentId,
        boolean isInstructor
) {
}
