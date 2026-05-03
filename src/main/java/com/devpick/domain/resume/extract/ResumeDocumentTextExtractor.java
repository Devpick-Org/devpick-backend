package com.devpick.domain.resume.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * PDF / DOCX에서 텍스트를 추출한다. OCR은 하지 않는다(MVP).
 */
@Component
public class ResumeDocumentTextExtractor {

    private static final int MAX_TEXT_CHARS = 200_000;

    public enum Kind {
        PDF,
        DOCX
    }

    public Kind detectKind(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return Kind.PDF;
        }
        if (lower.endsWith(".docx")) {
            return Kind.DOCX;
        }
        return null;
    }

    public String extract(Kind kind, byte[] raw) throws IOException {
        String text = switch (kind) {
            case PDF -> extractPdf(raw);
            case DOCX -> extractDocx(raw);
        };
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        return text;
    }

    private static String extractPdf(byte[] raw) throws IOException {
        try (PDDocument doc = Loader.loadPDF(raw)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private static String extractDocx(byte[] raw) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(raw));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
