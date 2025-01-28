package su.knst.telegram.ai.utils.functions.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class DDGSearch {
    protected static final String url = "https://html.duckduckgo.com/html/?q=";

    public static List<SearchInfo> search(String request) throws IOException {
        Document doc =Jsoup.connect(url + URLEncoder.encode(request, Charset.defaultCharset())).get();
        Elements results = doc.body().getElementsByClass("result");

        return parseInfo(results);
    }

    protected static List<SearchInfo> parseInfo(Elements elements) {
        ArrayList<SearchInfo> searchInfos = new ArrayList<>();

        for (Element element : elements) {
            SearchInfo searchInfo = new SearchInfo();

            Elements title = element.getElementsByClass("result__title");
            Elements url = element.getElementsByClass("result__url");
            Elements description = element.getElementsByClass("result__snippet");

            if (!title.isEmpty())
                searchInfo.setTitle(title.text());

            if (!url.isEmpty())
                searchInfo.setUrl(url.get(0).attributes().get("href"));

            if (!description.isEmpty())
                searchInfo.setDescription(description.text());

            searchInfos.add(searchInfo);
        }

        return searchInfos;
    }

}
