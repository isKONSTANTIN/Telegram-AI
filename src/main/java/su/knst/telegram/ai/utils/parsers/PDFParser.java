package su.knst.telegram.ai.utils.parsers;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Optional;

public class PDFParser {
    public static Optional<String> parse(File file) {
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
}
