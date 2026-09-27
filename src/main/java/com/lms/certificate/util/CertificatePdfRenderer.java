package com.lms.certificate.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Vẽ chứng chỉ hoàn thành khóa học bằng OpenPDF, transcribe lại bố cục/màu/font từ bản demo
 * {@code doc/feat/Main-html/Main.dc.html} (đặc tả đầy đủ ở {@code doc/DacTa_ChucNangChungChi.md}
 * mục 5).
 *
 * <p><b>Vì sao vẽ tuyệt đối bằng {@link PdfContentByte} thay vì {@code Paragraph} xuôi dòng như
 * PDF Quiz cũ:</b> bố cục chứng chỉ canh giữa/canh 2 đầu rất chặt theo pixel (khung viền kép, 3
 * cột chân trang dàn đều) — {@code Paragraph} không kiểm soát được vị trí chính xác này.
 *
 * <p><b>Huy hiệu (badge/seal):</b> ảnh PNG tĩnh dùng chung cho MỌI chứng chỉ (BR-CERT-08), nhúng
 * thẳng vào classpath tại {@code badge/badge-seal.png} (nguồn: {@code doc/img/Badge.png}, đã cắt
 * sát viền nội dung — ảnh gốc 2048×2048px có rất nhiều lề trong suốt xung quanh). Không lưu B2
 * theo từng bản ghi vì ảnh không đổi theo dữ liệu — xem mục 6 đặc tả.
 *
 * <p>Toạ độ mốc lấy từ canvas demo 1200×850px, quy đổi sang điểm PDF theo {@link #buildScale}
 * (khớp tỉ lệ khung khi ép vào A4 ngang — mục 5 đặc tả). Bản demo dùng
 * {@code flex-direction:column;justify-content:space-between} cho 3 khối lớn (header/nội dung
 * giữa/chân trang) thay vì toạ độ cố định — các mốc Y giữa khối ở đây là ước lượng lại cho khớp
 * tỉ lệ hiển thị trong ảnh chụp bản demo, không phải toạ độ pixel tuyệt đối 1-1.
 */
public final class CertificatePdfRenderer {

    /** Tăng số này khi đổi thiết kế chứng chỉ — buộc {@code CertificateService} render lại PDF
     * thay vì trả bản cache cũ trên B2 (BR-CERT-09). */
    public static final int TEMPLATE_VERSION = 1;

    private static final float CANVAS_W = 1200f;
    private static final float CANVAS_H = 850f;
    private static final float CENTER_X = 600f;

    private static final Color NAVY = new Color(0x1B, 0x2A, 0x4A);
    /** Xanh dương thật của logo hệ thống ({@code tailwind.config.ts} — {@code accent: #2563EB}),
     * KHÁC bảng màu vàng-navy trang trọng còn lại của chứng chỉ — cố ý giữ nguyên màu logo gốc
     * để chứng chỉ nhận diện đúng thương hiệu, theo yêu cầu (26/09/2026). */
    private static final Color ACCENT_BLUE = new Color(0x25, 0x63, 0xEB);
    private static final Color GOLD = new Color(0xB8, 0x89, 0x2B);
    private static final Color GOLD_TEXT = new Color(0x8C, 0x6D, 0x1F);
    private static final Color SLATE = new Color(0x5B, 0x64, 0x72);
    private static final Color CREAM = new Color(0xFB, 0xF8, 0xF0);
    private static final Color LINE_GREY = new Color(0xD8, 0xCD, 0xA9);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record CertificateData(
            String studentName,
            String courseName,
            BigDecimal courseHours,
            String completionDate,
            String instructorName,
            String certificateCode,
            String verifyUrl
    ) {}

    private CertificatePdfRenderer() {
    }

    public static byte[] render(CertificateData data) {
        try {
            float pageW = PageSize.A4.rotate().getWidth();
            float pageH = PageSize.A4.rotate().getHeight();
            float scale = pageH / CANVAS_H;
            float offsetX = (pageW - CANVAS_W * scale) / 2f;

            Document document = new Document(new com.lowagie.text.Rectangle(pageW, pageH), 0, 0, 0, 0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            Ctx ctx = new Ctx(cb, scale, offsetX, pageW, pageH);

            drawBackground(ctx);
            drawBorders(ctx);
            drawHeader(ctx);
            drawCenterBlock(ctx, data);
            drawFooter(ctx, data);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Khong sinh duoc PDF chung chi", e);
        }
    }

    /** Quy đổi toạ độ px (hệ mockup, gốc trên-trái) sang điểm PDF (gốc dưới-trái) + font đã nạp
     * sẵn — gói lại truyền xuyên suốt các hàm vẽ, không phải state khả biến. */
    private record Ctx(PdfContentByte cb, float scale, float offsetX, float pageW, float pageH,
                        BaseFont playfairBold, BaseFont playfairItalic, BaseFont playfairBoldItalic,
                        BaseFont workSansRegular, BaseFont workSansSemiBold) {
        Ctx(PdfContentByte cb, float scale, float offsetX, float pageW, float pageH) {
            this(cb, scale, offsetX, pageW, pageH,
                    load(CertificateFonts::playfairBold), load(CertificateFonts::playfairItalic),
                    load(CertificateFonts::playfairBoldItalic), load(CertificateFonts::workSansRegular),
                    load(CertificateFonts::workSansSemiBold));
        }

        /** Hoành độ px (mockup) -> điểm PDF. */
        float x(float px) {
            return offsetX + px * scale;
        }

        /** Tung độ px (mockup, tính từ mép trên) -> điểm PDF (tính từ mép dưới). */
        float y(float px) {
            return pageH - px * scale;
        }

        /** Quy đổi thuần 1 độ dài (không phải toạ độ) từ px sang điểm — dùng cho font-size, bề
         * rộng/cao hình vẽ. */
        float pt(float px) {
            return px * scale;
        }
    }

    @FunctionalInterface
    private interface FontLoader {
        BaseFont load() throws IOException, DocumentException;
    }

    private static BaseFont load(FontLoader loader) {
        try {
            return loader.load();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Khong nap duoc font chung chi", e);
        }
    }

    private static void drawBackground(Ctx ctx) {
        PdfContentByte cb = ctx.cb();
        cb.setColorFill(CREAM);
        cb.rectangle(0, 0, ctx.pageW(), ctx.pageH());
        cb.fill();
    }

    /** Khung viền kép — viền ngoài cách mép 26px (2pt, gold), viền trong cách mép 38px (1pt, gold
     * 45% alpha) — mục 5.1. */
    private static void drawBorders(Ctx ctx) {
        PdfContentByte cb = ctx.cb();
        drawInsetRect(ctx, 26f, 2f, GOLD, 1f);
        cb.saveState();
        cb.setGState(strokeAlpha(0.45f));
        drawInsetRect(ctx, 38f, 1f, GOLD, 1f);
        cb.restoreState();
    }

    private static void drawInsetRect(Ctx ctx, float insetPx, float lineWidthPx, Color color, float alpha) {
        PdfContentByte cb = ctx.cb();
        cb.setLineWidth(ctx.pt(lineWidthPx));
        cb.setColorStroke(color);
        float x = ctx.x(insetPx);
        float yTop = ctx.y(insetPx);
        float w = ctx.pt(CANVAS_W - 2 * insetPx);
        float h = ctx.pt(CANVAS_H - 2 * insetPx);
        cb.rectangle(x, yTop - h, w, h);
        cb.stroke();
    }

    private static PdfGState strokeAlpha(float alpha) {
        PdfGState gs = new PdfGState();
        gs.setStrokeOpacity(alpha);
        return gs;
    }

    /** Header: logo "LinguaLearn" + tagline + đường kẻ trang trí (mục 5.2), canh giữa ngang tại
     * {@link #CENTER_X}, tâm dòng logo đặt ở y≈83px (mép trên vùng nội dung 66px + nửa 34px). */
    private static void drawHeader(Ctx ctx) {
        PdfContentByte cb = ctx.cb();
        float logoSize = 34f;
        float logoCenterY = 83f;

        String lingua = "Lingua";
        String learn = "Learn";
        float wordmarkSizePt = ctx.pt(26);
        float wordmarkW = ctx.playfairBold().getWidthPoint(lingua + learn, wordmarkSizePt);
        float groupW = ctx.pt(logoSize) + ctx.pt(10) + wordmarkW;
        float logoX = ctx.x(CENTER_X) - groupW / 2f;

        // Ô vuông bo góc — LOGO THẬT của hệ thống (ô nền xanh accent + chữ "L" trắng, đúng như
        // `Header.tsx`), thay cho icon tam giác "play" trang trí của bản demo gốc (26/09/2026,
        // theo yêu cầu: chứng chỉ phải mang đúng logo hệ thống).
        float boxBottomPx = logoCenterY + logoSize / 2f;
        cb.setColorFill(ACCENT_BLUE);
        cb.roundRectangle(logoX, ctx.y(boxBottomPx), ctx.pt(logoSize), ctx.pt(logoSize), ctx.pt(10));
        cb.fill();

        float logoLetterSizePt = ctx.pt(18);
        float logoBoxCx = logoX + ctx.pt(logoSize) / 2f;
        float logoLetterBaseline = ctx.y(logoCenterY) - logoLetterSizePt * 0.36f;
        cb.beginText();
        cb.setFontAndSize(ctx.workSansSemiBold(), logoLetterSizePt);
        cb.setColorFill(CREAM);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "L", logoBoxCx, logoLetterBaseline, 0);
        cb.endText();

        // Chữ "Lingua" (navy) + "Learn" (gold) nối liền, baseline canh giữa chiều dọc ô logo.
        float textX = logoX + ctx.pt(logoSize) + ctx.pt(10);
        float textBaseline = ctx.y(logoCenterY) - wordmarkSizePt * 0.36f;
        cb.beginText();
        cb.setFontAndSize(ctx.playfairBold(), wordmarkSizePt);
        cb.setColorFill(NAVY);
        cb.setTextMatrix(textX, textBaseline);
        cb.showText(lingua);
        cb.endText();
        float linguaW = ctx.playfairBold().getWidthPoint(lingua, wordmarkSizePt);
        cb.beginText();
        cb.setFontAndSize(ctx.playfairBold(), wordmarkSizePt);
        cb.setColorFill(GOLD);
        cb.setTextMatrix(textX + linguaW, textBaseline);
        cb.showText(learn);
        cb.endText();

        // Mục 5.2 đặc tả — dòng này KHÔNG khai báo font-weight riêng trong bản demo (chỉ kế thừa
        // mặc định `body{font-family:'Work Sans'}` = Regular 400), khác nhãn "CHỨNG NHẬN..." bên
        // dưới có `font-weight:600` tường minh — 2 dòng trông gần giống nhau nhưng KHÁC độ đậm.
        centeredText(ctx, ctx.workSansRegular(), ctx.pt(11), GOLD_TEXT,
                spaced("AI-POWERED LEARNING PLATFORM"), CENTER_X, 111f);

        drawDivider(ctx, 128f);
    }

    private static void drawDivider(Ctx ctx, float dividerYpx) {
        PdfContentByte cb = ctx.cb();
        float half = 120f;
        float y = ctx.y(dividerYpx);
        cb.setLineWidth(ctx.pt(1));
        cb.setColorStroke(LINE_GREY);
        cb.moveTo(ctx.x(CENTER_X - half), y);
        cb.lineTo(ctx.x(CENTER_X - 6), y);
        cb.moveTo(ctx.x(CENTER_X + 6), y);
        cb.lineTo(ctx.x(CENTER_X + half), y);
        cb.stroke();

        float d = ctx.pt(5);
        float cx = ctx.x(CENTER_X);
        cb.setColorStroke(GOLD);
        cb.moveTo(cx - d, y);
        cb.lineTo(cx, y + d);
        cb.lineTo(cx + d, y);
        cb.lineTo(cx, y - d);
        cb.closePath();
        cb.stroke();
    }

    /** Khối nội dung chính: nhãn + tên học viên + tên khóa học + dòng meta (mục 5.3). Xếp từ
     * y=300px, dùng cursor cộng dồn theo đúng khoảng cách (gap) của bản demo. */
    private static void drawCenterBlock(Ctx ctx, CertificateData data) {
        float cursorY = 300f;

        cursorY += 12;
        centeredText(ctx, ctx.workSansSemiBold(), ctx.pt(12), GOLD_TEXT,
                spaced("CHỨNG NHẬN HOÀN THÀNH KHÓA HỌC"), CENTER_X, cursorY);
        cursorY += rowHeight(12) + 10;

        centeredText(ctx, ctx.playfairItalic(), ctx.pt(15), SLATE, "Chứng chỉ này được trao cho", CENTER_X, cursorY);
        cursorY += rowHeight(15) + 14;

        centeredText(ctx, ctx.playfairBoldItalic(), ctx.pt(46), NAVY, data.studentName(), CENTER_X, cursorY);
        // Chữ Playfair Display cỡ lớn có phần đuôi/dấu (ư, ệ...) vươn khá xa dưới baseline — chừa
        // dư thêm so với rowHeight() thông thường để gạch chân không đè lên chữ.
        cursorY += 46f * 1.35f;

        PdfContentByte cb = ctx.cb();
        cb.setLineWidth(ctx.pt(2));
        cb.setColorStroke(GOLD);
        cb.moveTo(ctx.x(CENTER_X - 110), ctx.y(cursorY));
        cb.lineTo(ctx.x(CENTER_X + 110), ctx.y(cursorY));
        cb.stroke();
        cursorY += 22;

        centeredText(ctx, ctx.playfairItalic(), ctx.pt(15), SLATE, "đã hoàn thành xuất sắc khóa học", CENTER_X, cursorY);
        cursorY += rowHeight(15) + 24;

        cursorY = drawWrappedCourseName(ctx, data.courseName(), cursorY);
        cursorY += 20;

        drawMetaRow(ctx, data, cursorY);
    }

    /** Ước lượng chiều cao 1 dòng chữ (cap-height + descender) theo cỡ chữ px — dùng để cộng dồn
     * {@code cursorY} giữa các phần tử xếp dọc, vì lớp vẽ này không có khái niệm "line box" thật
     * như CSS (khác bản demo HTML — xem docblock lớp). */
    private static float rowHeight(float fontSizePx) {
        return fontSizePx * 1.2f;
    }

    /** Tên khóa học (max-width 720px, có thể xuống 2 dòng) — dùng {@link ColumnText} để tự ngắt
     * dòng canh giữa, khác các nhãn ngắn khác vẽ thẳng 1 dòng. Trả về Y (px) ngay dưới khối vừa vẽ. */
    private static float drawWrappedCourseName(Ctx ctx, String courseName, float topYpx) {
        float fontSizePt = ctx.pt(24);
        float leadingPt = ctx.pt(32);
        float halfWidthPt = ctx.pt(720f) / 2f;
        float topPt = ctx.y(topYpx);

        Phrase phrase = new Phrase("“" + courseName + "”", new Font(ctx.playfairBold(), fontSizePt, Font.NORMAL, NAVY));
        ColumnText column = new ColumnText(ctx.cb());
        column.setSimpleColumn(ctx.x(CENTER_X) - halfWidthPt, topPt - leadingPt * 3, ctx.x(CENTER_X) + halfWidthPt, topPt + leadingPt,
                leadingPt, Element.ALIGN_CENTER);
        column.addText(phrase);
        try {
            column.go();
        } catch (DocumentException e) {
            throw new IllegalStateException("Khong ve duoc ten khoa hoc tren chung chi", e);
        }
        int linesUsed = Math.max(1, column.getLinesWritten());
        return topYpx + linesUsed * 32f;
    }

    private static void drawMetaRow(Ctx ctx, CertificateData data, float topYpx) {
        String hoursLabel = "Thời lượng: " + formatHours(data.courseHours()) + " giờ học";
        String dateLabel = "Ngày hoàn thành: " + data.completionDate();
        float metaSizePt = ctx.pt(13);
        float gapPt = ctx.pt(28);
        float w1 = ctx.workSansRegular().getWidthPoint(hoursLabel, metaSizePt);
        float w2 = ctx.workSansRegular().getWidthPoint(dateLabel, metaSizePt);
        float totalW = w1 + gapPt + w2;
        float startX = ctx.x(CENTER_X) - totalW / 2f;

        leftText(ctx, ctx.workSansRegular(), metaSizePt, SLATE, hoursLabel, startX, topYpx);
        leftText(ctx, ctx.workSansRegular(), metaSizePt, SLATE, dateLabel, startX + w1 + gapPt, topYpx);
    }

    /** Chân trang 3 cột dàn 2 đầu, canh đáy: QR+mã (trái) · huy hiệu (giữa) · 2 chữ ký (phải)
     * (mục 5.4). {@code footerBottom} khớp mép dưới vùng nội dung (850-66=784px). */
    private static void drawFooter(Ctx ctx, CertificateData data) {
        float footerBottom = 784f;
        drawQrColumn(ctx, data, footerBottom);
        drawBadge(ctx, footerBottom);
        drawSignatures(ctx, data, footerBottom);
    }

    private static void drawQrColumn(Ctx ctx, CertificateData data, float footerBottom) {
        float qrSizePx = 34f;
        float qrLeftX = 80f;
        float qrTopY = footerBottom - qrSizePx;

        BufferedImage qrImage = CertificateQrCode.generate(data.verifyUrl(), 128);
        try {
            Image pdfImage = Image.getInstance(qrImage, null);
            pdfImage.scaleAbsolute(ctx.pt(qrSizePx), ctx.pt(qrSizePx));
            pdfImage.setAbsolutePosition(ctx.x(qrLeftX), ctx.y(footerBottom));
            ctx.cb().addImage(pdfImage);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Khong nhung duoc QR code vao PDF", e);
        }

        float textX = qrLeftX + qrSizePx + 10;
        // "MÃ CHỨNG CHỈ" không có font-weight riêng trong bản demo (Regular 400) — chỉ chính mã
        // {{certificateId}} ngay dưới mới có font-weight:600 tường minh.
        leftText(ctx, ctx.workSansRegular(), ctx.pt(9), SLATE, spaced("MÃ CHỨNG CHỈ"), ctx.x(textX), qrTopY + 8);
        leftText(ctx, ctx.workSansSemiBold(), ctx.pt(12), NAVY, data.certificateCode(), ctx.x(textX), qrTopY + 21);
        leftText(ctx, ctx.workSansRegular(), ctx.pt(11), GOLD_TEXT, data.verifyUrl(), ctx.x(textX), qrTopY + 34);
    }

    /** Huy hiệu (BR-CERT-08) — ảnh PNG tĩnh {@code badge/badge-seal.png}, rộng 70px (mockup-px)
     * đúng bằng đường kính vòng tròn ở bản demo gốc; chiều cao suy ra theo đúng tỉ lệ ảnh gốc nên
     * phần dải ruy băng phía dưới tự nhiên thò xuống dưới {@code footerBottom} một chút — giống
     * hệt cách bản demo để dải ruy băng "thò" ra ngoài baseline chung của hàng chân trang. */
    private static void drawBadge(Ctx ctx, float footerBottom) {
        try {
            Image badgeImage = Image.getInstance(loadBadgeImageBytes());
            float displayWidthPx = 70f;
            float displayHeightPx = displayWidthPx * badgeImage.getHeight() / badgeImage.getWidth();
            float leftPx = CENTER_X - displayWidthPx / 2f;
            float topPx = footerBottom - 70f;

            badgeImage.scaleAbsolute(ctx.pt(displayWidthPx), ctx.pt(displayHeightPx));
            badgeImage.setAbsolutePosition(ctx.x(leftPx), ctx.y(topPx) - ctx.pt(displayHeightPx));
            ctx.cb().addImage(badgeImage);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Khong nhung duoc anh huy hieu vao PDF", e);
        }
    }

    private static volatile byte[] badgeImageBytesCache;

    private static byte[] loadBadgeImageBytes() throws IOException {
        byte[] cached = badgeImageBytesCache;
        if (cached != null) {
            return cached;
        }
        try (var in = CertificatePdfRenderer.class.getClassLoader().getResourceAsStream("badge/badge-seal.png")) {
            if (in == null) {
                throw new IOException("Khong tim thay anh huy hieu tren classpath: badge/badge-seal.png");
            }
            byte[] bytes = in.readAllBytes();
            badgeImageBytesCache = bytes;
            return bytes;
        }
    }

    private static void drawSignatures(Ctx ctx, CertificateData data, float footerBottom) {
        float rightEdge = 1120f;
        float sigWidth = 140f;
        float gap = 34f;
        float col2CenterX = rightEdge - sigWidth / 2f;
        float col1CenterX = col2CenterX - sigWidth - gap;

        drawSignature(ctx, data.instructorName(), "GIẢNG VIÊN PHỤ TRÁCH", col1CenterX, sigWidth, footerBottom);
        drawSignature(ctx, "LinguaLearn", "ĐẠI DIỆN NỀN TẢNG", col2CenterX, sigWidth, footerBottom);
    }

    private static void drawSignature(Ctx ctx, String name, String label, float centerXpx, float widthPx, float footerBottom) {
        float nameTopY = footerBottom - 46f;
        float lineY = footerBottom - 16f;
        float labelTopY = footerBottom - 12f;

        centeredText(ctx, ctx.playfairItalic(), ctx.pt(19), NAVY, name, centerXpx, nameTopY);

        PdfContentByte cb = ctx.cb();
        cb.setLineWidth(ctx.pt(1));
        cb.setColorStroke(SLATE);
        cb.moveTo(ctx.x(centerXpx - widthPx / 2f), ctx.y(lineY));
        cb.lineTo(ctx.x(centerXpx + widthPx / 2f), ctx.y(lineY));
        cb.stroke();

        // Nhãn "GIẢNG VIÊN PHỤ TRÁCH"/"ĐẠI DIỆN NỀN TẢNG" không có font-weight riêng trong bản
        // demo (Regular 400).
        centeredText(ctx, ctx.workSansRegular(), ctx.pt(10), SLATE, spaced(label), centerXpx, labelTopY);
    }

    // ── Tiện ích vẽ text ─────────────────────────────────────────────────────────

    /** Vẽ 1 dòng text canh giữa tại {@code centerXpx}, {@code topYpx} coi như mép trên dòng chữ —
     * lùi baseline xuống ~78% cỡ chữ để xấp xỉ đúng vị trí baseline thật (không cần chính xác
     * tuyệt đối, chỉ cần nhất quán giữa các dòng). */
    private static void centeredText(Ctx ctx, BaseFont font, float sizePt, Color color, String text, float centerXpx, float topYpx) {
        PdfContentByte cb = ctx.cb();
        float baseline = ctx.y(topYpx) - sizePt * 0.78f;
        cb.beginText();
        cb.setFontAndSize(font, sizePt);
        cb.setColorFill(color);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, text, ctx.x(centerXpx), baseline, 0);
        cb.endText();
    }

    private static void leftText(Ctx ctx, BaseFont font, float sizePt, Color color, String text, float xPt, float topYpx) {
        PdfContentByte cb = ctx.cb();
        float baseline = ctx.y(topYpx) - sizePt * 0.78f;
        cb.beginText();
        cb.setFontAndSize(font, sizePt);
        cb.setColorFill(color);
        cb.setTextMatrix(xPt, baseline);
        cb.showText(text);
        cb.endText();
    }

    /** Giả lập letter-spacing (OpenPDF không có API letter-spacing trực tiếp cho
     * {@code showTextAligned}) bằng khoảng trắng mảnh (U+2009 thin space) chèn giữa từng ký tự —
     * đủ dùng cho các nhãn viết hoa ngắn của thiết kế này. */
    private static String spaced(String upperText) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upperText.length(); i++) {
            sb.append(upperText.charAt(i));
            if (i < upperText.length() - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String formatHours(BigDecimal hours) {
        BigDecimal stripped = hours.stripTrailingZeros();
        return stripped.scale() <= 0
                ? stripped.toBigInteger().toString()
                : hours.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FMT);
    }
}
