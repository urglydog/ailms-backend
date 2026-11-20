package com.lms.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.report.util.SimpleXlsxWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class SimpleXlsxWriterTest {

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                files.put(e.getName(), new String(zin.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void writesAValidPackageWithEscapedTextAndNumericCells() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SimpleXlsxWriter w = new SimpleXlsxWriter(out)) {
            w.sheet("Tóm tắt: [1]", List.<Object[]>of(new Object[] {"a<b & \"c\"", 12345L}, new Object[] {"=1+1", null, 2.5}));
        }

        Map<String, String> f = unzip(out.toByteArray());
        assertThat(f).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml");
        String sheet = f.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("a&lt;b &amp; &quot;c&quot;").contains("<c r=\"B1\"><v>12345</v></c>").contains("<c r=\"C2\"><v>2.5</v></c>");
        // Công thức không bao giờ được ghi dạng <f>: chỉ là chuỗi inline.
        assertThat(sheet).doesNotContain("<f>").contains("t=\"inlineStr\"");
        // Ký tự cấm trong tên sheet (: [ ]) được thay bằng khoảng trắng.
        assertThat(f.get("xl/workbook.xml")).contains("name=\"Tóm tắt   1\"");
    }

    @Test
    void stopsAtTheRowLimitAndStripsInvalidControlCharacters() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < SimpleXlsxWriter.MAX_ROWS + 10; i++) {
            rows.add(new Object[] {i == 0 ? "x\u0001y" : "r" + i});
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SimpleXlsxWriter w = new SimpleXlsxWriter(out)) {
            w.sheet("S", rows);
        }

        String sheet = unzip(out.toByteArray()).get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains(">xy<").doesNotContain("\u0001");
        assertThat(sheet).contains("r=\"" + SimpleXlsxWriter.MAX_ROWS + "\"").doesNotContain("r=\"" + (SimpleXlsxWriter.MAX_ROWS + 1) + "\"");
    }
}
