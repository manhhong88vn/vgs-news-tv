package com.vgs.newstv;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewsDreamService extends DreamService {

    private ImageView imgCover;
    private TextView txtClock;
    private TextView txtCategory;
    private TextView txtTitle;
    private TextView txtDesc;

    private final Handler slideHandler = new Handler(Looper.getMainLooper());
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private final String[] RSS_URLS = {
        "https://vnexpress.net/rss/tin-noi-bat.rss",
        "https://vnexpress.net/rss/kinh-doanh.rss",
        "https://vnexpress.net/rss/giai-tri.rss",
        "https://vnexpress.net/rss/oto-xe-may.rss"
    };

    private final String[] CATEGORY_NAMES = {
        "🔥 TIN NỔI BẬT",
        "💰 KINH TẾ",
        "🎭 GIẢI TRÍ",
        "🚗 XE HƠI"
    };

    private List<NewsItem> newsList = new ArrayList<>();
    private int currentCategoryIndex = 0;
    private int currentNewsIndex = 0;

    // 12 giây đổi tin một lần
    private static final long SLIDE_INTERVAL = 12000;
    // 15 phút tải mới tin tức từ mạng
    private static final long REFRESH_INTERVAL = 900000;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // Chạm hoặc bấm phím bất kỳ sẽ thoát Screensaver ngay
        setFullscreen(true);
        setContentView(R.layout.screensaver_news);

        imgCover = findViewById(R.id.dreamImage);
        txtClock = findViewById(R.id.dreamClock);
        txtCategory = findViewById(R.id.dreamCategory);
        txtTitle = findViewById(R.id.dreamTitle);
        txtDesc = findViewById(R.id.dreamDesc);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        startClock();
        loadNewsForCurrentCategory();
        startAutoSlide();
        startAutoRefresh();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        slideHandler.removeCallbacksAndMessages(null);
        clockHandler.removeCallbacksAndMessages(null);
        refreshHandler.removeCallbacksAndMessages(null);
    }

    private void startClock() {
        clockHandler.post(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                if (txtClock != null) {
                    txtClock.setText(sdf.format(new Date()));
                }
                clockHandler.postDelayed(this, 1000);
            }
        });
    }

    private void loadNewsForCurrentCategory() {
        new Thread(() -> {
            List<NewsItem> items = RssFetcher.fetch(RSS_URLS[currentCategoryIndex]);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (items != null && !items.isEmpty()) {
                    newsList = items;
                    currentNewsIndex = 0;
                    displayCurrentNews();
                }
            });
        }).start();
    }

    private void displayCurrentNews() {
        if (newsList == null || newsList.isEmpty()) return;
        if (currentNewsIndex >= newsList.size()) {
            currentNewsIndex = 0;
            // Luân phiên chuyển sang chuyên mục kế tiếp
            currentCategoryIndex = (currentCategoryIndex + 1) % RSS_URLS.length;
            loadNewsForCurrentCategory();
            return;
        }

        NewsItem item = newsList.get(currentNewsIndex);
        txtCategory.setText(CATEGORY_NAMES[currentCategoryIndex]);
        txtTitle.setText(item.getTitle());
        txtDesc.setText(item.getDescription());

        String imgUrl = item.getImageUrl();
        if (imgUrl != null && !imgUrl.isEmpty() && UrlGuard.isImage(imgUrl)) {
            imgCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(imgUrl).centerCrop().into(imgCover);
        } else {
            imgCover.setVisibility(View.GONE);
        }
    }

    private void startAutoSlide() {
        slideHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                currentNewsIndex++;
                displayCurrentNews();
                slideHandler.postDelayed(this, SLIDE_INTERVAL);
            }
        }, SLIDE_INTERVAL);
    }

    private void startAutoRefresh() {
        refreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadNewsForCurrentCategory();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        }, REFRESH_INTERVAL);
    }
}
