package com.lms.report.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bộ ghi XLSX tối giản, GHI DẠNG LUỒNG (không dựng cả bảng tính trong RAM, không thêm thư viện — quyết định 15/11/2026 vì server chỉ 3,8GB).
 * Mỗi sheet được ghi thẳng vào zip theo từng dòng; chuỗi dùng inline string (không có công thức nên không bị formula injection),
 * số ghi dạng số. Giới hạn {@link #MAX_ROWS} dòng mỗi sheet.
 */
public final class SimpleXlsxWriter implements AutoCloseable {

    public static final int MAX_ROWS = 50_000;

    private final ZipOutputStream zip;
    private final List<String> sheetNames = new ArrayList<>();
    private boolean closed;

    public SimpleXlsxWriter(OutputStream out) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    }

    /** Ghi một sheet; {@code rows} là danh sách dòng, ô là {@link Number} hoặc chuỗi (null = ô trống). Dòng thừa quá giới hạn bị bỏ. */
    public void sheet(String name, Iterable<Object[]> rows) throws IOException {
        sheetNames.add(name);
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheetNames.size() + ".xml"));
        StringBuilder head = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        zip.write(head.toString().getBytes(StandardCharsets.UTF_8));
        int r = 0;
        for (Object[] row : rows) {
            if (++r > MAX_ROWS) {
                break;
            }
            StringBuilder sb = new StringBuilder("<row r=\"").append(r).append("\">");
            for (int c = 0; c < row.length; c++) {
                Object v = row[c];
                if (v == null) {
                    continue;
                }
                String ref = column(c) + r;
                if (v instanceof Number n) {
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(n).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(v.toString()))
                            .append("</t></is></c>");
                }
            }
            sb.append("</row>");
            zip.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        zip.write("</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Ghi các phần còn lại của gói XLSX và đóng luồng; gọi sau khi đã ghi hết các sheet. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        entry("[Content_Types].xml", contentTypes());
        entry("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheetNames.size(); i++) {
            wb.append("<sheet name=\"").append(escape(sheetName(sheetNames.get(i)))).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        wb.append("</sheets></workbook>");
        rels.append("</Relationships>");
        entry("xl/workbook.xml", wb.toString());
        entry("xl/_rels/workbook.xml.rels", rels.toString());
        zip.finish();
        zip.close();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheetNames.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private void entry(String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Tên sheet Excel: tối đa 31 ký tự, không chứa : \ / ? * [ ]. */
    static String sheetName(String name) {
        String n = name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return n.length() > 31 ? n.substring(0, 31) : (n.isEmpty() ? "Sheet" : n);
    }

    static String column(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        do {
            sb.insert(0, (char) ('A' + i % 26));
            i = i / 26 - 1;
        } while (i >= 0);
        return sb.toString();
    }

    /** Escape XML và bỏ ký tự điều khiển không hợp lệ trong XML 1.0. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&') sb.append("&amp;");
            else if (ch == '<') sb.append("&lt;");
            else if (ch == '>') sb.append("&gt;");
            else if (ch == '"') sb.append("&quot;");
            else if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) sb.append(ch);
        }
        return sb.toString();
    }
}
