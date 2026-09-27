package com.lms.catalog.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Sinh slug từ tiêu đề tiếng Việt (bỏ dấu, thay "đ/Đ", chỉ giữ a-z0-9 và dấu gạch ngang).
 * Dùng chung cho {@code Category} và {@code Course} — cả hai đều cần slug duy nhất cho URL.
 */
public final class SlugGenerator {

    private SlugGenerator() {
    }

    public static String slugify(String input) {
        String slug = stripAccentsLower(input)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        return slug.isBlank() ? "khoa-hoc" : slug;
    }

    /** Bỏ dấu tiếng Việt + viết thường, GIỮ NGUYÊN khoảng trắng/dấu câu (khác {@link #slugify} —
     * dùng cho so khớp văn bản dạng heuristic thay vì sinh URL, xem
     * {@code com.lms.chat.service.TutorSecurityService}). */
    public static String stripAccentsLower(String input) {
        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return noAccent.toLowerCase(Locale.ROOT);
    }
}
