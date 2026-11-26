package com.lms.chat.dto;

import java.util.Map;

/** Gợi ý câu hỏi cho Gia sư AI (26/11/2026). */
public class TutorSuggestionDto {

    /**
     * @param text   câu hỏi hiển thị/gửi đi (tiếng Việt với TEMPLATE — FE dịch lại theo key)
     * @param source POPULAR | AI | TEMPLATE
     * @param key    mã mẫu (chỉ với TEMPLATE): summary | quiz | hardest | relation | chapter
     * @param params tham số điền vào mẫu (lesson, previous, chapter)
     */
    public record SuggestionRes(String text, String source, String key, Map<String, String> params) {}
}
