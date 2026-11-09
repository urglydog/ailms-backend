package com.lms.common.util;

import com.lowagie.text.pdf.BaseFont;

import java.io.IOException;
import java.io.InputStream;

/**
 * Font tiếng Việt dùng chung cho mọi PDF sinh bằng OpenPDF (Certificate, đề thi Quiz...).
 *
 * <p>Base-14 Helvetica mặc định của OpenPDF chỉ hỗ trợ Cp1252, không có glyph cho ký tự
 * tiếng Việt (ư, ơ, ệ, Đ...). DejaVu Sans hỗ trợ đầy đủ khối Latin Extended Additional cần
 * cho tiếng Việt, đã có sẵn dưới dạng font hệ thống — đóng gói thẳng vào classpath
 * (`src/main/resources/fonts/`) để không phụ thuộc font cài trên máy chủ lúc chạy.
 */
public final class VietnamesePdfFonts {

    private VietnamesePdfFonts() {
    }

    public static BaseFont loadRegular() throws IOException, com.lowagie.text.DocumentException {
        return load("fonts/DejaVuSans.ttf");
    }

    public static BaseFont loadBold() throws IOException, com.lowagie.text.DocumentException {
        return load("fonts/DejaVuSans-Bold.ttf");
    }

    private static BaseFont load(String classpathLocation) throws IOException, com.lowagie.text.DocumentException {
        byte[] fontBytes = readClasspathFont(classpathLocation);
        return BaseFont.createFont(classpathLocation, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                BaseFont.CACHED, fontBytes, null);
    }

    private static byte[] readClasspathFont(String classpathLocation) throws IOException {
        try (InputStream in = VietnamesePdfFonts.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IOException("Khong tim thay font tren classpath: " + classpathLocation);
            }
            return in.readAllBytes();
        }
    }
}
