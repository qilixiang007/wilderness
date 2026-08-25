package com.wilderness.backend.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 必应国内版联网检索源(无需 key)。
 * 抓取搜索结果页,解析标题 / 链接 / 摘要。结果多为百度百科、NASA 等天文权威站点,契合"去官网查"的诉求。
 */
@Component
public class BingWebSearch implements WebSearchSource {

    private static final String SEARCH_URL = "https://cn.bing.com/search?q=%s&count=%d";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    @Override
    public String name() {
        return "Bing";
    }

    @Override
    public List<WebResult> search(String query, int limit) throws Exception {
        String url = SEARCH_URL.formatted(URLEncoder.encode(query, StandardCharsets.UTF_8), limit);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

        List<WebResult> results = new ArrayList<>();
        for (Element li : doc.select("li.b_algo")) {
            Element a = li.selectFirst("h2 a");
            if (a == null) {
                continue;
            }
            String title = a.text().trim();
            String href = a.attr("href");
            if (title.isEmpty() || !href.startsWith("http")) {
                continue;
            }
            Element caption = li.selectFirst(".b_caption");
            String snippet = caption == null ? "" : caption.text().trim();
            results.add(new WebResult(title, href, snippet));
        }
        return results;
    }
}
