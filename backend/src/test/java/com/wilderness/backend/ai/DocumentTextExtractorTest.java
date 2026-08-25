package com.wilderness.backend.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证文件解析器对 txt / docx / pdf / xlsx 的实际解析(不依赖 LLM,离线可跑)。 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void txt() throws IOException {
        DocumentTextExtractor.Extracted r = extractor.extract("a.txt", "你好,星空".getBytes(StandardCharsets.UTF_8));
        assertEquals("你好,星空", r.text());
    }

    @Test
    void md() throws IOException {
        DocumentTextExtractor.Extracted r = extractor.extract("a.md", "# 标题\n正文内容".getBytes(StandardCharsets.UTF_8));
        assertEquals("# 标题\n正文内容", r.text());
    }

    @Test
    void docx() throws IOException {
        byte[] bytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("木星是太阳系最大的行星");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            bytes = bos.toByteArray();
        }
        DocumentTextExtractor.Extracted r = extractor.extract("a.docx", bytes);
        assertTrue(r.text().contains("木星是太阳系最大的行星"));
    }

    @Test
    void pdf() throws IOException {
        byte[] bytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(60, 700);
                cs.showText("Hello wilderness");
                cs.endText();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            bytes = bos.toByteArray();
        }
        DocumentTextExtractor.Extracted r = extractor.extract("a.pdf", bytes);
        assertTrue(r.text().contains("Hello wilderness"));
    }

    @Test
    void xlsx() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("S1").createRow(0).createCell(0).setCellValue("离太阳最近的行星");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            bytes = bos.toByteArray();
        }
        DocumentTextExtractor.Extracted r = extractor.extract("a.xlsx", bytes);
        assertTrue(r.text().contains("离太阳最近的行星"));
    }

    @Test
    void unsupported() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract("a.zip", new byte[]{1}));
    }
}
