package com.vgs.newstv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.util.Log;
import androidx.tvprovider.media.tv.PreviewChannel;
import androidx.tvprovider.media.tv.PreviewChannelHelper;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hướng A (bản thử): đưa tin lên màn hình chính Android TV dưới dạng 1 hàng thẻ ("Tin nhanh").
 * Mọi lỗi đều được bắt và ghi log, không làm app crash nếu TV không hỗ trợ.
 */
public final class ChannelHelper {

    static final String ACTION_OPEN_NEWS = "com.vgs.newstv.OPEN_NEWS";

    private static final String TAG = "NewsChannel";
    private static final String PREFS = "newstv_channel";
    private static final String KEY_CHANNEL_ID = "channel_id";
    private static final String KEY_LAST_REFRESH = "last_refresh";

    private static final int PER_FEED = 10;          // tối đa 10 tin mỗi mục => tối đa 40 thẻ
    private static final int DESC_MAX = 300;
    private static final long STALE_MS = 30L * 60 * 1000;

    private ChannelHelper() {}

    /** Preview Channel chỉ có từ Android 8.0 (API 26) trên thiết bị TV (leanback). */
    public static boolean isSupported(Context ctx) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && ctx.getPackageManager().hasSystemFeature("android.software.leanback");
    }

    /** Nhẹ, an toàn gọi trên luồng chính: chỉ lên lịch tác vụ nền, việc nặng làm ở NewsRefreshJobService. */
    public static void setup(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!isSupported(app)) {
            Log.i(TAG, "Thiết bị không hỗ trợ Preview Channel, bỏ qua.");
            return;
        }
        try {
            NewsRefreshJobService.schedulePeriodic(app);
            SharedPreferences sp = prefs(app);
            boolean never = sp.getLong(KEY_CHANNEL_ID, -1) < 0;
            boolean stale = System.currentTimeMillis() - sp.getLong(KEY_LAST_REFRESH, 0) > STALE_MS;
            if (never || stale) NewsRefreshJobService.scheduleNow(app);
        } catch (Throwable t) {
            Log.w(TAG, "setup lỗi", t);
        }
    }

    /** Chạy ở luồng nền. Trả về true nếu thành công (hoặc không cần làm gì). */
    // synchronized: tránh 2 lượt làm mới chạy chồng nhau (sẽ đăng thẻ trùng).
    static synchronized boolean refresh(Context ctx) {
        if (!isSupported(ctx)) return true;
        try {
            long channelId = ensureChannel(ctx);

            List<List<NewsItem>> feeds = new ArrayList<>();
            for (String url : RssFetcher.FEEDS) {
                try {
                    feeds.add(RssFetcher.fetch(url, PER_FEED));
                } catch (Exception e) {
                    Log.w(TAG, "Không tải được " + url, e);
                }
            }
            List<NewsItem> merged = interleave(feeds);
            if (merged.isEmpty()) return false;   // giữ thẻ cũ, thử lại sau

            PreviewChannelHelper helper = new PreviewChannelHelper(ctx);
            List<Long> oldIds = programIds(ctx, channelId);

            int published = 0;
            int weight = merged.size();   // weight lớn hơn = hiện trước, giữ đúng thứ tự xen kẽ
            for (NewsItem item : merged) {
                try {
                    helper.publishPreviewProgram(buildProgram(ctx, channelId, item, weight));
                    published++;
                } catch (Exception e) {
                    Log.w(TAG, "Không đăng được thẻ", e);
                }
                weight--;
            }
            if (published == 0) return false;

            // Chỉ xóa thẻ cũ sau khi đã có thẻ mới, để hàng không bị trống giữa chừng
            for (long id : oldIds) {
                try {
                    helper.deletePreviewProgram(id);
                } catch (Exception e) {
                    Log.w(TAG, "Không xóa được thẻ cũ", e);
                }
            }
            prefs(ctx).edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "refresh lỗi", t);
            return false;
        }
    }

    private static long ensureChannel(Context ctx) throws Exception {
        long id = prefs(ctx).getLong(KEY_CHANNEL_ID, -1);
        if (channelExists(ctx, id)) return id;

        PreviewChannel channel = new PreviewChannel.Builder()
                .setDisplayName("Tin nhanh")
                .setAppLinkIntent(new Intent(ctx, MainActivity.class))
                .setLogo(buildLogo())
                .build();
        long newId = new PreviewChannelHelper(ctx).publishDefaultChannel(channel);
        prefs(ctx).edit().putLong(KEY_CHANNEL_ID, newId).apply();
        return newId;
    }

    private static boolean channelExists(Context ctx, long id) {
        if (id < 0) return false;
        try (Cursor c = ctx.getContentResolver().query(
                TvContractCompat.buildChannelUri(id), new String[]{BaseColumns._ID}, null, null, null)) {
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Long> programIds(Context ctx, long channelId) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(
                TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                new String[]{BaseColumns._ID}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) ids.add(c.getLong(0));
            }
        } catch (Exception e) {
            Log.w(TAG, "Không đọc được danh sách thẻ cũ", e);
        }
        return ids;
    }

    private static PreviewProgram buildProgram(Context ctx, long channelId, NewsItem item, int weight) {
        String desc = item.getDescription();
        if (desc.length() > DESC_MAX) desc = desc.substring(0, DESC_MAX) + "…";

        Intent open = new Intent(ctx, MainActivity.class)
                .setAction(ACTION_OPEN_NEWS)
                .putExtra("title", item.getTitle())
                .putExtra("link", item.getLink())
                .putExtra("image", item.getImageUrl())
                .putExtra("description", desc);

        PreviewProgram.Builder b = new PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle(item.getTitle())
                .setDescription(desc)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setWeight(weight)
                .setInternalProviderId(item.getLink())
                .setIntent(open);
        if (UrlGuard.isImage(item.getImageUrl())) {
            b.setPosterArtUri(Uri.parse(item.getImageUrl()));
        }
        return b.build();
    }

    /** Xen kẽ các mục để những thẻ đầu tiên có đủ nổi bật/kinh tế/giải trí/xe, bỏ trùng link. */
    private static List<NewsItem> interleave(List<List<NewsItem>> feeds) {
        List<NewsItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int max = 0;
        for (List<NewsItem> f : feeds) max = Math.max(max, f.size());
        for (int i = 0; i < max; i++) {
            for (List<NewsItem> f : feeds) {
                if (i < f.size()) {
                    NewsItem n = f.get(i);
                    if (UrlGuard.isArticle(n.getLink()) && seen.add(n.getLink())) out.add(n);
                }
            }
        }
        return out;
    }

    /** Logo vuông vẽ bằng code (chữ "T" trắng trên nền xanh), không phụ thuộc file icon. */
    private static Bitmap buildLogo() {
        Bitmap bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.parseColor("#1565C0"));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(96f);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("T", 80f, 80f - (p.descent() + p.ascent()) / 2f, p);
        return bmp;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
