package com.vgs.newstv;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NewsAdapter adapter;
    private Button tabFeatured, tabEconomy, tabEntertainment, tabCars;
    private TextView txtStatus;

    // View cho chế độ PiP
    private View normalContainer, pipContainer;
    private ImageView pipImage;
    private TextView pipTitle, pipCategory;

    private static final String RSS_FEATURED = "https://vnexpress.net/rss/tin-noi-bat.rss";
    private static final String RSS_ECONOMY = "https://vnexpress.net/rss/kinh-doanh.rss";
    private static final String RSS_ENTERTAINMENT = "https://vnexpress.net/rss/giai-tri.rss";
    private static final String RSS_CARS = "https://vnexpress.net/rss/oto-xe-may.rss";

    private final String[] CATEGORY_NAMES = {"🔥 NỔI BẬT", "💰 KINH TẾ", "🎭 GIẢI TRÍ", "🚗 XE HƠI"};
    private final String[] RSS_URLS = {RSS_FEATURED, RSS_ECONOMY, RSS_ENTERTAINMENT, RSS_CARS};

    // Quản lý tác vụ tải dữ liệu (tránh xung đột khi đổi tab nhanh)
    private FetchNewsTask currentTask = null;
    private String currentRssUrl = RSS_FEATURED;
    private List<NewsItem> cachedNewsList = new ArrayList<>();

    // Handlers tự động cập nhật
    private final Handler autoSlideHandler = new Handler(Looper.getMainLooper());
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());

    private int currentCategoryIndex = 0;
    private int currentNewsIndex = 0;

    // Chu kỳ 10 giây đổi tin trong PiP, 10 phút tự làm mới tin
    private static final long SLIDE_INTERVAL = 10000;
    private static final long REFRESH_INTERVAL = 600000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mở từ thẻ trên màn hình chính: đi thẳng tới màn hình đọc tin, không vào PiP/danh sách
        if (openNewsFromCard(getIntent())) {
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        normalContainer = findViewById(R.id.normalContainer);
        pipContainer = findViewById(R.id.pipContainer);
        pipImage = findViewById(R.id.pipImage);
        pipTitle = findViewById(R.id.pipTitle);
        pipCategory = findViewById(R.id.pipCategory);
        txtStatus = findViewById(R.id.txtStatus);

        recyclerView = findViewById(R.id.newsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new NewsAdapter(this, new ArrayList<>());
        recyclerView.setAdapter(adapter);

        tabFeatured = findViewById(R.id.tabFeatured);
        tabEconomy = findViewById(R.id.tabEconomy);
        tabEntertainment = findViewById(R.id.tabEntertainment);
        tabCars = findViewById(R.id.tabCars);

        tabFeatured.setOnClickListener(v -> selectTab(tabFeatured, RSS_FEATURED, 0));
        tabEconomy.setOnClickListener(v -> selectTab(tabEconomy, RSS_ECONOMY, 1));
        tabEntertainment.setOnClickListener(v -> selectTab(tabEntertainment, RSS_ENTERTAINMENT, 2));
        tabCars.setOnClickListener(v -> selectTab(tabCars, RSS_CARS, 3));

        selectTab(tabFeatured, RSS_FEATURED, 0);

        // BƯỚC 1: Tự động kích hoạt PiP khi mở app (nếu hệ thống hỗ trợ)
        enterPipModeIfSupported();

        // BƯỚC 2: Tự động làm mới tin mỗi 10 phút
        startAutoRefreshTimer();

        // Hướng A: đăng ký/làm mới hàng thẻ tin trên màn hình chính (không làm gì nếu TV không hỗ trợ)
        ChannelHelper.setup(this);

        // Khởi động dịch vụ lắng nghe màn hình chờ tự động
        try {
            Intent serviceIntent = new Intent(this, ScreensaverBridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    // Mở tin từ thẻ Preview Channel. Intent này có thể đến từ app khác nên kiểm tra miền trước khi dùng.
    private boolean openNewsFromCard(Intent in) {
        if (in == null || !ChannelHelper.ACTION_OPEN_NEWS.equals(in.getAction())) return false;
        String title = in.getStringExtra("title");
        String link = in.getStringExtra("link");
        if (title == null || !UrlGuard.isArticle(link)) return false;

        String image = in.getStringExtra("image");
        if (!UrlGuard.isImage(image)) {
            image = "";
        }
        String desc = in.getStringExtra("description");

        Intent detail = new Intent(this, DetailActivity.class);
        detail.putExtra("title", title);
        detail.putExtra("image", image);
        detail.putExtra("description", desc == null ? "" : desc);
        // SUA LOI GOP BAN: link da duoc kiem tra HTTPS vnexpress.net o tren,
        // phai chuyen tiep sang DetailActivity thi moi tai duoc bai day du.
        // Ban zip goc thieu dong nay nen chi hien mo ta RSS.
        detail.putExtra("link", link);
        startActivity(detail);
        return true;
    }

    private void enterPipModeIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    PictureInPictureParams params = new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9))
                            .build();
                    enterPictureInPictureMode(params);
                } catch (Exception e) {
                    // Nếu thiết bị không hỗ trợ PiP thì tiếp tục chạy giao diện thường
                    e.printStackTrace();
                }
            }, 800);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            normalContainer.setVisibility(View.GONE);
            pipContainer.setVisibility(View.VISIBLE);
            startPipSlideshow();
        } else {
            normalContainer.setVisibility(View.VISIBLE);
            pipContainer.setVisibility(View.GONE);
            stopPipSlideshow();
        }
    }

    private void selectTab(Button selectedBtn, String rssUrl, int categoryIndex) {
        tabFeatured.setSelected(false);
        tabEconomy.setSelected(false);
        tabEntertainment.setSelected(false);
        tabCars.setSelected(false);

        selectedBtn.setSelected(true);
        currentRssUrl = rssUrl;
        currentCategoryIndex = categoryIndex;

        // BƯỚC 2: Hủy tác vụ cũ khi đổi tab nhanh
        if (currentTask != null && currentTask.getStatus() == AsyncTask.Status.RUNNING) {
            currentTask.cancel(true);
        }

        txtStatus.setVisibility(View.VISIBLE);
        txtStatus.setText("Đang tải tin tức...");

        currentTask = new FetchNewsTask();
        currentTask.execute(rssUrl);
    }

    private void startAutoRefreshTimer() {
        autoRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                selectTab(getCurrentTabButton(), currentRssUrl, currentCategoryIndex);
                autoRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        }, REFRESH_INTERVAL);
    }

    private Button getCurrentTabButton() {
        switch (currentCategoryIndex) {
            case 1: return tabEconomy;
            case 2: return tabEntertainment;
            case 3: return tabCars;
            default: return tabFeatured;
        }
    }

    // Tự đổi tin mỗi 10 giây và đổi chuyên mục luân phiên trong PiP
    private void startPipSlideshow() {
        autoSlideHandler.post(slideRunnable);
    }

    private void stopPipSlideshow() {
        autoSlideHandler.removeCallbacks(slideRunnable);
    }

    private final Runnable slideRunnable = new Runnable() {
        @Override
        public void run() {
            if (cachedNewsList != null && !cachedNewsList.isEmpty()) {
                if (currentNewsIndex >= cachedNewsList.size()) {
                    currentNewsIndex = 0;
                    // Đổi sang chuyên mục tiếp theo luân phiên
                    currentCategoryIndex = (currentCategoryIndex + 1) % RSS_URLS.length;
                    selectTab(getCurrentTabButton(), RSS_URLS[currentCategoryIndex], currentCategoryIndex);
                    // Phải hẹn lượt kế tiếp, nếu không cửa sổ nhỏ sẽ đứng im sau chuyên mục đầu tiên.
                    autoSlideHandler.postDelayed(this, SLIDE_INTERVAL);
                    return;
                }

                NewsItem item = cachedNewsList.get(currentNewsIndex);
                pipTitle.setText(item.getTitle());
                pipCategory.setText(CATEGORY_NAMES[currentCategoryIndex]);

                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    Glide.with(MainActivity.this)
                            .load(item.getImageUrl())
                            .centerCrop()
                            .into(pipImage);
                }
                currentNewsIndex++;
            }
            autoSlideHandler.postDelayed(this, SLIDE_INTERVAL);
        }
    };

    private class FetchNewsTask extends AsyncTask<String, Void, List<NewsItem>> {
        private boolean isNetworkError = false;

        @Override
        protected List<NewsItem> doInBackground(String... urls) {
            try {
                return RssFetcher.fetch(urls[0], 20);
            } catch (Exception e) {
                isNetworkError = true;
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(List<NewsItem> result) {
            if (isCancelled()) return;

            if (isNetworkError || result == null || result.isEmpty()) {
                txtStatus.setVisibility(View.VISIBLE);
                txtStatus.setText("⚠️ Lỗi kết nối mạng! Vui lòng kiểm tra lại Wi-Fi.");
            } else {
                txtStatus.setVisibility(View.GONE);
                cachedNewsList = result;
                currentNewsIndex = 0;   // danh sách mới thì chạy từ tin đầu
                adapter.updateData(result);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPipSlideshow();
        autoRefreshHandler.removeCallbacksAndMessages(null);
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }
}
