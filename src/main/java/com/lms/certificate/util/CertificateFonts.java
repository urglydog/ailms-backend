package com.lms.certificate.util;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;
import java.io.IOException;
import java.io.InputStream;

/**
 * Font trang trọng dùng riêng cho chứng chỉ (Playfair Display + Work Sans), theo đúng bản demo ở
 * {@code doc/feat/Main-html/Main.dc.html} — KHÁC {@link com.lms.common.util.VietnamesePdfFonts}
 * (DejaVu Sans, dùng cho đề thi Quiz và các PDF nghiệp vụ thuần, không cần thẩm mỹ).
 *
 * <p>Cả 2 họ font gốc trên Google Fonts đều là <b>variable font</b> (1 file, trục {@code wght}).
 * OpenPDF (nhân iText 2.x cũ) không đọc được bảng {@code fvar}/{@code gvar} nên chỉ render đúng 1
 * trọng lượng mặc định của file — vì vậy 5 file {@code .ttf} nhúng ở {@code src/main/resources/fonts/}
 * đã được "instance hoá" thành font tĩnh đúng từng trọng lượng cần dùng (qua
 * {@code fonttools varLib.instancer}, xem lịch sử trao đổi khi thêm tính năng), KHÔNG phải file
 * gốc tải thẳng từ Google Fonts.
 */
public final class CertificateFonts {

    private CertificateFonts() {
    }

    public static BaseFont playfairBold() throws IOException, DocumentException {
        return load("fonts/PlayfairDisplay-Bold.ttf");
    }

    public static BaseFont playfairItalic() throws IOException, DocumentException {
        return load("fonts/PlayfairDisplay-Italic.ttf");
    }

    public static BaseFont playfairBoldItalic() throws IOException, DocumentException {
        return load("fonts/PlayfairDisplay-BoldItalic.ttf");
    }

    public static BaseFont workSansRegular() throws IOException, DocumentException {
        return load("fonts/WorkSans-Regular.ttf");
    }

    public static BaseFont workSansSemiBold() throws IOException, DocumentException {
        return load("fonts/WorkSans-SemiBold.ttf");
    }

    private static BaseFont load(String classpathLocation) throws IOException, DocumentException {
        byte[] fontBytes = readClasspathFont(classpathLocation);
        return BaseFont.createFont(classpathLocation, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                BaseFont.CACHED, fontBytes, null);
    }

    private static byte[] readClasspathFont(String classpathLocation) throws IOException {
        try (InputStream in = CertificateFonts.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IOException("Khong tim thay font tren classpath: " + classpathLocation);
            }
            return in.readAllBytes();
        }
    }
}
