package edu.whu.developer.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final int MAX_PAGES = 40;
    private static final int MAX_CHARS = 12000;

    public String extract(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() <= 0) {
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));
            String text = stripper.getText(document);
            if (text == null) {
                return "";
            }
            String normalized = text.replace("\u0000", " ").trim().replaceAll("[ \\t\\x0B\\f\\r]+", " ");
            normalized = normalized.replaceAll("\\n{3,}", "\n\n").trim();
            if (normalized.length() > MAX_CHARS) {
                normalized = normalized.substring(0, MAX_CHARS);
            }
            log.info(
                    "PDF extract pages={}/{} chars={}",
                    Math.min(document.getNumberOfPages(), MAX_PAGES),
                    document.getNumberOfPages(),
                    normalized.length());
            return normalized;
        } catch (Exception ex) {
            log.warn("PDF extract failed: {}", ex.getMessage());
            return "";
        }
    }
}
