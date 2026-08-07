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
        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String slug = noAccent.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        return slug.isBlank() ? "khoa-hoc" : slug;
    }
}
