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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static Optional<String> parseHtmlWithLinks(File file) {
        try {
            Document doc = Jsoup.parse(file, "UTF-8");
            Element body = doc.body();

            StringBuilder sb = new StringBuilder();
            body.traverse((node, depth) -> {
                if (node instanceof Element e && e.tagName().equals("a") && e.hasAttr("href")) {
                    sb.append(e.attr("href"))
                            .append(": ");
                } else if (node instanceof org.jsoup.nodes.TextNode) {
                    sb.append(((org.jsoup.nodes.TextNode) node).text()).append(" ");
                }
            });

            return Optional.of(sb.toString());
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

    public static String markdown2telegram(String markdown) {
        // Convert bold
        markdown = markdown.replaceAll("\\*\\*(.*?)\\*\\*", "*$1*");

        // Convert italic
        markdown = markdown.replaceAll("_(.*?)_", "_$1_");

        // Convert underline
        markdown = markdown.replaceAll("__(.*?)__", "__$1__");

        // Convert strikethrough
        markdown = markdown.replaceAll("~~(.*?)~~", "~$1~");

        // Convert spoiler
        markdown = markdown.replaceAll("\\|\\|(.*?)\\|\\|", "||$1||");

        // Convert inline URL and mention
        Pattern urlPattern = Pattern.compile("\\[(.*?)\\]\\((http[s]?:\\/\\/[^)]+)\\)");
        Matcher urlMatcher = urlPattern.matcher(markdown);
        while (urlMatcher.find()) {
            String text = urlMatcher.group(1);
            String url = urlMatcher.group(2);
            String replacement = "[" + text + "](" + url + ")";
            markdown = markdown.replace(urlMatcher.group(0), replacement);
        }

        Pattern mentionPattern = Pattern.compile("\\[(.*?)\\]\\((tg:\\/\\/user\\?id=([0-9]+))\\)");
        Matcher mentionMatcher = mentionPattern.matcher(markdown);
        while (mentionMatcher.find()) {
            String text = mentionMatcher.group(1);
            String userId = mentionMatcher.group(2);
            String replacement = "[" + text + "](" + userId + ")";
            markdown = markdown.replace(mentionMatcher.group(0), replacement);
        }

        return markdown;
    }
}
