package com.lms.finance;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** So khớp tên chủ tài khoản ngân hàng (thường VIẾT HOA không dấu) với tên đã xác minh của giảng viên (11/11/2026). */
public final class NameNormalizer {

    private NameNormalizer() {}

    /** Bỏ dấu, đ→d, in hoa, chỉ giữ chữ cái và khoảng trắng, gọn khoảng trắng. */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return s;
    }

    /** Hai tên khớp nếu cùng tập từ (không quan tâm thứ tự họ/tên). */
    public static boolean matches(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        Set<String> ta = new HashSet<>(Arrays.asList(na.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(nb.split(" ")));
        return ta.equals(tb);
    }
}
