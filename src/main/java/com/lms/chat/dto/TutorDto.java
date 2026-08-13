package com.lms.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTO cho {@code POST /api/v1/lessons/{lessonId}/tutor/ask} (UC30). */
public class TutorDto {

    public record AskReq(
            @NotBlank @Size(max = 2000) String question,
            /** Bỏ trống ở tin đầu tiên — {@link com.lms.chat.service.TutorService} tự tạo
             * {@code ChatSession} mới hoặc tái sử dụng phiên gần nhất của (học viên, bài học). */
            Long sessionId
    ) {}

    public record AskRes(
            Long sessionId,
            String answer,
            /** Giây, BR-TUTOR-02 — luôn có ≥1 phần tử khi câu trả lời liên quan bài giảng. */
            List<Integer> citedTimestamps,
            Integer tokenUsed
    ) {}
}
