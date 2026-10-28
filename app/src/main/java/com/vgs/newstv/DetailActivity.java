package com.vgs.newstv;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Man hinh doc bai day du.
 *
 * - Lay link tu RSS (extra "link"), chi chap nhan HTTPS + host vnexpress.net.
 * - Tai trang bang Jsoup tren luong nen, timeout 10 giay, khong mo
 *   trinh duyet nhung, khong API ben ngoai, khong them quyen ngoai INTERNET.
 * - Tach: h1.title-detail, p.description (sapô), article.fck_detail
 *   (cac p, h2/h3, anh + chu thich). Co fallback neu VnExpress doi cau truc.
 * - That bai / mat mang / link khong hop le: hien mo ta RSS cu kem thong bao
 *   "Khong tai duoc bai day du", khong de man hinh trong.
 * - Remote: ScrollView giu focus, phim len/xuong cuon trang.
 *   Nut Back mac dinh quay lai danh sach (activity stack, khong can code them).
 */
public class DetailActivity extends AppCompatActivity {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private ScrollView scrollView;
    private TextView txtWarning;
    private TextView txtStatus;
    private TextView txtTitle;
    private ImageView imgCover;
    private TextView txtDescription;
    private LinearLayout bodyContainer;

    private String rssTitle = "";
    private String rssImage = "";
    private String rssDescription = "";
    private String rssLink = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;

    private enum Kind { TEXT, LEAD, HEADING, IMAGE, CAPTION }

    private static class Block {
        final Kind kind;
        final String text;
        final String imageUrl;

        Block(Kind kind, String text, String imageUrl) {
            this.kind = kind;
            this.text = text;
            this.imageUrl = imageUrl;
        }
    }

    private static class Article {
        final String title;
        final String coverUrl;
        final List<Block> blocks;

        Article(String title, String coverUrl, List<Block> blocks) {
            this.title = title;
            this.coverUrl = coverUrl;
            this.blocks = blocks;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        scrollView = findViewById(R.id.detailScrollView);
        txtWarning = findViewById(R.id.detailWarning);
        txtStatus = findViewById(R.id.detailStatus);
        txtTitle = findViewById(R.id.detailTitle);
        imgCover = findViewById(R.id.detailImage);
        txtDescription = findViewById(R.id.detailDescription);
        bodyContainer = findViewById(R.id.detailBodyContainer);

        rssTitle = getStringExtra("title");
        rssImage = getStringExtra("image");
        rssDescription = getStringExtra("description");
        rssLink = getStringExtra("link");

        // Tieu de RSS hien ngay, khong doi tai xong moi co chu.
        txtTitle.setText(rssTitle.isEmpty() ? "Tin tức" : rssTitle);
        txtWarning.setVisibility(View.GONE);
        txtDescription.setVisibility(View.GONE);
        bodyContainer.removeAllViews();

        // Anh RSS lam anh tam trong luc tai bai day du.
        if (!rssImage.isEmpty()) {
            imgCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(rssImage).centerCrop().into(imgCover);
        } else {
            imgCover.setVisibility(View.GONE);
        }

        // ScrollView giu focus de remote len/xuong cuon duoc ngay.
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.requestFocus();
            }
        });

        if (!isValidArticleUrl(rssLink)) {
            // Khong thu tai link la, link rong, http hoac host khac VnExpress.
            showFallback();
            return;
        }

        txtStatus.setVisibility(View.VISIBLE);
        txtStatus.setText("Đang tải bài đầy đủ...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Article article = null;
                try {
                    article = fetchArticle(rssLink);
                } catch (Exception e) {
                    article = null;
                }
                final Article result = article;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        if (result == null || !hasBodyContent(result)) {
                            showFallback();
                        } else {
                            renderFullArticle(result);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    /**
     * Remote len/xuong: cuon 3/4 chieu cao man hinh moi lan bam.
     * Chi chan phim khi ScrollView con cuon duoc theo huong do,
     * neu khong thi tra ve he thong xu ly nhu binh thuong.
     * Nut Back khong bi chan o day, mac dinh quay lai danh sach.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && scrollView != null
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP)) {
            int direction = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
            if (scrollView.canScrollVertically(direction)) {
                int step = scrollView.getHeight() * 3 / 4;
                scrollView.smoothScrollBy(0, direction * step);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ---------------- Hien thi ----------------

    private void renderFullArticle(Article article) {
        txtStatus.setVisibility(View.GONE);
        txtWarning.setVisibility(View.GONE);
        txtDescription.setVisibility(View.GONE);
        bodyContainer.removeAllViews();

        if (!article.title.isEmpty()) {
            txtTitle.setText(article.title);
        }

        String cover = !article.coverUrl.isEmpty() ? article.coverUrl : rssImage;
        if (!cover.isEmpty()) {
            imgCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(cover).centerCrop().into(imgCover);
        } else {
            imgCover.setVisibility(View.GONE);
        }

        for (Block block : article.blocks) {
            // Anh bia da hien o tren thi khong lap lai trong than bai.
            if (block.kind == Kind.IMAGE
                    && !cover.isEmpty()
                    && cover.equalsIgnoreCase(block.imageUrl)) {
                continue;
            }
            View view = buildBlockView(block);
            if (view != null) {
                bodyContainer.addView(view);
            }
        }

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
                scrollView.requestFocus();
            }
        });
    }

    private void showFallback() {
        txtStatus.setVisibility(View.GONE);
        txtWarning.setVisibility(View.VISIBLE);
        txtWarning.setText("Không tải được bài đầy đủ");
        bodyContainer.removeAllViews();

        if (!rssImage.isEmpty()) {
            imgCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(rssImage).centerCrop().into(imgCover);
        } else {
            imgCover.setVisibility(View.GONE);
        }

        // Mo ta RSS cu, khong de man hinh trong. Neu ca mo ta cung rong
        // thi van con tieu de + thong bao o tren.
        if (!rssDescription.isEmpty()) {
            txtDescription.setVisibility(View.VISIBLE);
            txtDescription.setText(rssDescription);
        } else {
            txtDescription.setVisibility(View.GONE);
        }

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
                scrollView.requestFocus();
            }
        });
    }

    private View buildBlockView(Block block) {
        switch (block.kind) {
            case LEAD:
                return makeTextView(block.text, 26, true, 0xFFE0E0E0, 24);
            case HEADING:
                return makeTextView(block.text, 28, true, 0xFFFFFFFF, 16);
            case CAPTION:
                TextView caption = makeTextView(block.text, 18, false, 0xFFB0BEC5, 24);
                caption.setTypeface(null, Typeface.ITALIC);
                caption.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                return caption;
            case IMAGE: {
                ImageView imageView = new ImageView(this);
                imageView.setFocusable(false);
                imageView.setAdjustViewBounds(true);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(8);
                imageView.setLayoutParams(params);
                Glide.with(this).load(block.imageUrl).fitCenter().into(imageView);
                return imageView;
            }
            case TEXT:
            default:
                return makeTextView(block.text, 24, false, 0xFFE0E0E0, 24);
        }
    }

    private TextView makeTextView(String text, int sizeSp, boolean bold,
                                  int color, int marginBottomDp) {
        TextView textView = new TextView(this);
        textView.setFocusable(false);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        textView.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        textView.setTextColor(color);
        textView.setLineSpacing(0, 1.4f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(marginBottomDp);
        textView.setLayoutParams(params);
        return textView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String getStringExtra(String key) {
        String value = getIntent() != null ? getIntent().getStringExtra(key) : null;
        return value == null ? "" : value;
    }

    private boolean hasBodyContent(Article article) {
        for (Block block : article.blocks) {
            if (block.kind == Kind.TEXT || block.kind == Kind.HEADING
                    || block.kind == Kind.IMAGE) {
                return true;
            }
        }
        return false;
    }

    // ---------------- Tai va tach bai ----------------

    private boolean isValidArticleUrl(String link) {
        if (link == null || link.isEmpty()) return false;
        try {
            URL url = new URL(link);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return false;
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase();
            return host.equals("vnexpress.net") || host.endsWith(".vnexpress.net");
        } catch (Exception e) {
            return false;
        }
    }

    private Article fetchArticle(String link) throws Exception {
        Document doc = Jsoup.connect(link)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(0)
                .followRedirects(true)
                .get();

        // Tieu de: h1.title-detail, roi meta og:title, roi <title>.
        String title = "";
        Element h1 = doc.selectFirst("h1.title-detail");
        if (h1 != null) title = clean(h1.text());
        if (title.isEmpty()) {
            Element ogTitle = doc.selectFirst("meta[property=og:title]");
            if (ogTitle != null) title = clean(ogTitle.attr("content"));
        }
        if (title.isEmpty()) {
            title = clean(doc.title()).replaceAll("\\s*-\\s*VnExpress\\s*$", "");
        }

        // Anh bia: meta og:image truoc, neu khong co thi lay anh dau tien trong bai.
        String cover = "";
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) cover = clean(ogImage.attr("content"));

        // Sapô: p.description dung lam doan mo dau.
        String lead = "";
        Element desc = doc.selectFirst("p.description");
        if (desc != null) lead = clean(desc.text());

        Element body = findBody(doc);
        if (body == null) return null;

        Element cleaned = body.clone();
        cleaned.select("script, style, noscript, iframe, video, form, button, "
                + "nav, header, footer, aside").remove();
        cleaned.select(".box-related, .list-news, .tag, .author, .social, "
                + ".box_category, [class*=ads], [class*=advert]").remove();

        List<Block> blocks = new ArrayList<>();
        if (!lead.isEmpty()) {
            blocks.add(new Block(Kind.LEAD, lead, null));
        }
        collectBlocks(cleaned, blocks);

        if (cover.isEmpty()) {
            for (Block block : blocks) {
                if (block.kind == Kind.IMAGE && block.imageUrl != null
                        && !block.imageUrl.isEmpty()) {
                    cover = block.imageUrl;
                    break;
                }
            }
        }

        return new Article(title, cover, blocks);
    }

    private Element findBody(Document doc) {
        String[] selectors = {
                "article.fck_detail",
                ".fck_detail",
                "article.article-content",
                ".article-content",
                "article"
        };
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null && el.select("p").size() >= 2) {
                return el;
            }
        }
        return null;
    }

    private void collectBlocks(Element parent, List<Block> blocks) {
        for (Element child : parent.children()) {
            if (isJunk(child)) continue;
            String tag = child.tagName();

            if (tag.equals("p")) {
                Element img = child.selectFirst("img");
                if (img != null) {
                    addImageBlock(child, img, blocks);
                    continue;
                }
                String cls = child.className();
                if (cls.contains("Image") || cls.contains("caption")
                        || cls.contains("Caption")) {
                    String caption = clean(child.text());
                    if (!caption.isEmpty()) {
                        blocks.add(new Block(Kind.CAPTION, caption, null));
                    }
                    continue;
                }
                String text = clean(child.text());
                // Bo cac dong "Xem them" dan toi bai lien quan, khong phai than bai.
                if (text.toLowerCase().startsWith("xem thêm")) continue;
                if (!text.isEmpty()) {
                    blocks.add(new Block(Kind.TEXT, text, null));
                }
            } else if (tag.equals("h2") || tag.equals("h3")) {
                String text = clean(child.text());
                if (!text.isEmpty()) {
                    blocks.add(new Block(Kind.HEADING, text, null));
                }
            } else if (tag.equals("figure")) {
                Element img = child.selectFirst("img");
                if (img != null) {
                    addImageBlock(child, img, blocks);
                } else {
                    collectBlocks(child, blocks);
                }
            } else if (tag.equals("div")) {
                // Chi coi div la khoi anh khi no khong chua doan van dai nao
                // ngoai chu thich. Div chua nhieu doan van thi de quy xuong.
                Elements imgs = child.select("img");
                boolean hasLongText = false;
                for (Element p : child.select("p")) {
                    String cls = p.className();
                    if (!cls.contains("Image") && !cls.contains("caption")
                            && clean(p.text()).length() > 80) {
                        hasLongText = true;
                        break;
                    }
                }
                if (imgs.size() == 1 && !hasLongText) {
                    addImageBlock(child, imgs.first(), blocks);
                } else {
                    collectBlocks(child, blocks);
                }
            } else if (tag.equals("table")) {
                Element img = child.selectFirst("img");
                if (img != null) {
                    addImageBlock(child, img, blocks);
                } else {
                    collectBlocks(child, blocks);
                }
            } else if (tag.equals("ul") || tag.equals("ol")) {
                for (Element li : child.select("li")) {
                    String text = clean(li.text());
                    if (!text.isEmpty()) {
                        blocks.add(new Block(Kind.TEXT, "• " + text, null));
                    }
                }
            } else if (tag.equals("blockquote")) {
                String text = clean(child.text());
                if (!text.isEmpty()) {
                    blocks.add(new Block(Kind.TEXT, text, null));
                }
            } else {
                collectBlocks(child, blocks);
            }
        }
    }

    private void addImageBlock(Element container, Element img, List<Block> blocks) {
        String url = getImageUrl(img);
        if (url.isEmpty()) return;
        blocks.add(new Block(Kind.IMAGE, null, url));

        String caption = "";
        Element capEl = container.selectFirst(
                "figcaption, p.caption, p.Image, .caption, .fig-caption");
        if (capEl != null) caption = clean(capEl.text());
        if (caption.isEmpty() && container.tagName().equals("table")) {
            Elements rows = container.select("tr");
            if (rows.size() >= 2) {
                caption = clean(rows.last().text());
            }
        }
        if (!caption.isEmpty()) {
            blocks.add(new Block(Kind.CAPTION, caption, null));
        }
    }

    private String getImageUrl(Element img) {
        String src = img.absUrl("src");
        if (src.isEmpty() || src.startsWith("data:")) {
            src = img.absUrl("data-src");
        }
        if (src.isEmpty() || src.startsWith("data:")) {
            src = img.absUrl("data-original");
        }
        if ((src.isEmpty() || src.startsWith("data:"))
                && !img.attr("srcset").isEmpty()) {
            // srcset: lay URL dau tien, bo mo ta do rong "480w, 960w".
            String first = img.attr("srcset").split("[,\\s]+")[0];
            if (first.startsWith("http")) {
                src = first;
            } else if (!first.isEmpty()) {
                try {
                    src = new URL(new URL(img.baseUri()), first).toString();
                } catch (Exception ignored) {
                    src = "";
                }
            }
        }
        return src == null ? "" : src;
    }

    private boolean isJunk(Element el) {
        String tag = el.tagName();
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")
                || tag.equals("iframe") || tag.equals("video")
                || tag.equals("form") || tag.equals("button")
                || tag.equals("nav") || tag.equals("header")
                || tag.equals("footer") || tag.equals("aside")) {
            return true;
        }
        String cls = el.className() == null ? "" : el.className().toLowerCase();
        return cls.contains("box-related") || cls.contains("list-news")
                || cls.contains("box_category") || cls.contains("social")
                || cls.contains("ads") || cls.contains("advert")
                || cls.contains("related");
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
