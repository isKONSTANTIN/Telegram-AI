package su.knst.telegram.ai.utils.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class HtmlParser {
    public static Optional<String> parse(File file) {
        try {
            Document doc = Jsoup.parse(file, "UTF-8");
            Element body = doc.body();

            return Optional.of(body.text());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }
}
