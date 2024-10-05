package su.knst.telegram.ai.utils.parsers;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

public class DocxParser {
    public static Optional<String> parse(File file) {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs())
                content.append(paragraph.getText()).append("\n");

            return Optional.of(content.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }
}
