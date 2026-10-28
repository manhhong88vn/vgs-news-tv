package com.vgs.newstv;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NewsAdapter adapter;
    private Button tabFeatured, tabEconomy, tabEntertainment, tabCars;

    private static final String RSS_FEATURED = "https://vnexpress.net/rss/tin-noi-bat.rss";
    private static final String RSS_ECONOMY = "https://vnexpress.net/rss/kinh-doanh.rss";
    private static final String RSS_ENTERTAINMENT = "https://vnexpress.net/rss/giai-tri.rss";
    private static final String RSS_CARS = "https://vnexpress.net/rss/oto-xe-may.rss";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.newsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        adapter = new NewsAdapter(this, new ArrayList<>());
        recyclerView.setAdapter(adapter);

        tabFeatured = findViewById(R.id.tabFeatured);
        tabEconomy = findViewById(R.id.tabEconomy);
        tabEntertainment = findViewById(R.id.tabEntertainment);
        tabCars = findViewById(R.id.tabCars);

        tabFeatured.setOnClickListener(v -> selectTab(tabFeatured, RSS_FEATURED));
        tabEconomy.setOnClickListener(v -> selectTab(tabEconomy, RSS_ECONOMY));
        tabEntertainment.setOnClickListener(v -> selectTab(tabEntertainment, RSS_ENTERTAINMENT));
        tabCars.setOnClickListener(v -> selectTab(tabCars, RSS_CARS));

        // Mặc định chọn tab Nổi bật
        selectTab(tabFeatured, RSS_FEATURED);
    }

    private void selectTab(Button selectedBtn, String rssUrl) {
        tabFeatured.setSelected(false);
        tabEconomy.setSelected(false);
        tabEntertainment.setSelected(false);
        tabCars.setSelected(false);

        selectedBtn.setSelected(true);
        new FetchNewsTask().execute(rssUrl);
    }

    private class FetchNewsTask extends AsyncTask<String, Void, List<NewsItem>> {
        @Override
        protected List<NewsItem> doInBackground(String... urls) {
            List<NewsItem> items = new ArrayList<>();
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                InputStream is = conn.getInputStream();

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList nodeList = doc.getElementsByTagName("item");
                int count = Math.min(nodeList.getLength(), 20);

                for (int i = 0; i < count; i++) {
                    Element element = (Element) nodeList.item(i);
                    String title = getTagValue(element, "title");
                    String link = getTagValue(element, "link");
                    String rawDesc = getTagValue(element, "description");

                    String imageUrl = extractImageUrl(rawDesc);
                    String cleanDesc = cleanDescription(rawDesc);

                    items.add(new NewsItem(title, link, imageUrl, cleanDesc));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return items;
        }

        @Override
        protected void onPostExecute(List<NewsItem> result) {
            if (result != null && !result.isEmpty()) {
                adapter.updateData(result);
            }
        }
    }

    private String getTagValue(Element element, String tag) {
        NodeList nl = element.getElementsByTagName(tag);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        }
        return "";
    }

    private String extractImageUrl(String desc) {
        Pattern pattern = Pattern.compile("src=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(desc);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String cleanDescription(String desc) {
        return desc.replaceAll("<[^>]*>", "").trim();
    }
}
