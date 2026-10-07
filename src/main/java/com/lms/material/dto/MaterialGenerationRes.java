package com.lms.material.dto;

import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record MaterialGenerationRes(
        Long id,
        MaterialType materialType,
        String language,
        String title,
        Integer versionNo,
        GenStatus status,
        String celeryTaskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Chỉ có giá trị khi materialType=QUIZ ("LECTURE_QUIZ"/"OFFICIAL_EXAM") — FE/mobile cần
         * để phân biệt NGAY ở danh sách (trước khi mở chi tiết), tránh nhầm quiz ôn tập thường
         * với bài thi chính thức (bug thật 07/10/2026 bên mobile — danh sách trước đó hiện 2
         * loại giống hệt nhau, không cách nào phân biệt). null với mọi materialType khác. */
        String quizType
) {
}
