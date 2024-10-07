package su.knst.telegram.ai.utils.parsers;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TextConverters {
    public static Optional<String> parsePdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);

            if (text != null && !text.isBlank())
                return Optional.of(text);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public static Optional<String> parseHtml(File file) {
        try {
            Document doc = Jsoup.parse(file, "UTF-8");
            Element body = doc.body();

            return Optional.of(body.text());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public static Optional<String> docx2markdown(File file) {
        StringBuilder markdown = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String style = paragraph.getStyle();
                    if ("Title".equals(style)) {
                        markdown.append("# ").append(paragraph.getText()).append("\n\n");
                    } else if ("Heading1".equals(style)) {
                        markdown.append("## ").append(paragraph.getText()).append("\n\n");
                    } else if ("Heading2".equals(style)) {
                        markdown.append("### ").append(paragraph.getText()).append("\n\n");
                    } else {
                        markdown.append(paragraph.getText()).append("\n\n");
                    }

                    for (XWPFRun run : paragraph.getRuns()) {
                        if (run.isBold()) {
                            markdown.append("**").append(run.getText(0)).append("**");
                        } else if (run.isItalic()) {
                            markdown.append("_").append(run.getText(0)).append("_");
                        } else {
                            markdown.append(run.getText(0));
                        }
                        markdown.append(" ");
                    }
                    markdown.append("\n\n");
                }

                if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        markdown.append("| ");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            markdown.append(cell.getText()).append(" | ");
                        }
                        markdown.append("\n");
                    }
                    markdown.append("\n\n");
                }
            }
        } catch (IOException e) {
            return Optional.of("Failed to convert");
        }

        return Optional.of(markdown.toString());
    }
}
