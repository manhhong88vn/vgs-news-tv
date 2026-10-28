package com.vgs.newstv;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

/** Tải và đọc RSS VnExpress (dùng cho tác vụ nền cập nhật thẻ). Chỉ HTTPS, có timeout. */
final class RssFetcher {

    static final String[] FEEDS = {
            "https://vnexpress.net/rss/tin-noi-bat.rss",
            "https://vnexpress.net/rss/kinh-doanh.rss",
            "https://vnexpress.net/rss/giai-tri.rss",
            "https://vnexpress.net/rss/oto-xe-may.rss"
    };

    private static final Pattern IMG_SRC = Pattern.compile("src=\"([^\"]+)\"");

    private RssFetcher() {}

    static List<NewsItem> fetch(String feedUrl, int max) throws Exception {
        if (!feedUrl.startsWith("https://")) throw new IllegalArgumentException("Chỉ cho phép HTTPS");

        List<NewsItem> items = new ArrayList<>();
        HttpURLConnection conn = (HttpURLConnection) new URL(feedUrl).openConnection();
        try {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            try (InputStream is = conn.getInputStream()) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                NodeList nodes = doc.getElementsByTagName("item");
                int count = Math.min(nodes.getLength(), max);
                for (int i = 0; i < count; i++) {
                    Element e = (Element) nodes.item(i);
                    String title = tag(e, "title").trim();
                    String link = tag(e, "link").trim();
                    String rawDesc = tag(e, "description");
                    if (title.isEmpty() || link.isEmpty()) continue;

                    String image = "";
                    Matcher m = IMG_SRC.matcher(rawDesc);
                    if (m.find()) image = m.group(1);

                    items.add(new NewsItem(title, link, image, rawDesc.replaceAll("<[^>]*>", "").trim()));
                }
            }
        } finally {
            conn.disconnect();
        }
        return items;
    }

    private static String tag(Element e, String name) {
        NodeList nl = e.getElementsByTagName(name);
        return (nl != null && nl.getLength() > 0) ? nl.item(0).getTextContent() : "";
    }
}
