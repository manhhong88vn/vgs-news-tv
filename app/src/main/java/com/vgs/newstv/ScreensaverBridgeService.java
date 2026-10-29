package com.vgs.newstv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ScreensaverBridgeService extends Service {
    private static final String TAG = "ScreensaverBridge";
    private static final String CHANNEL_ID = "screensaver_bridge_channel";
    private BroadcastReceiver dynamicReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreensaverBridgeService created");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screensaver Bridge",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Tự động kích hoạt màn hình chờ");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Bảo vệ màn hình")
                    .setContentText("Dịch vụ tự động kích hoạt đang chạy")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .build();
            startForeground(1001, notification);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.duokan.intent.action.show.screensaver");
        filter.addAction("mitv.enter.xiaoai_mode");
        filter.addAction("com.duokan.intent.action.KEY_IDLE");

        dynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : "";
                Log.d(TAG, "Dynamic broadcast received: " + action);
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.neilturner.aerialviews", "com.neilturner.aerialviews.ui.screensaver.TestActivity"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(i);
                } catch (Exception e) {
                    Log.e(TAG, "Could not start AerialViews", e);
                }
            }
        };

        registerReceiver(dynamicReceiver, filter);
        Log.d(TAG, "Dynamic receiver registered successfully");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dynamicReceiver != null) {
            try {
                unregisterReceiver(dynamicReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
