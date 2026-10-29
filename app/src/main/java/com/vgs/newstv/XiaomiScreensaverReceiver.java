package com.vgs.newstv;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class XiaomiScreensaverReceiver extends BroadcastReceiver {
    private static final String TAG = "ScreensaverBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        Log.d(TAG, "Xiaomi screensaver broadcast received: " + action);
        if ("com.duokan.intent.action.show.screensaver".equals(action) || "mitv.enter.xiaoai_mode".equals(action)) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.neilturner.aerialviews", "com.neilturner.aerialviews.ui.screensaver.TestActivity"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(i);
            } catch (Exception e) {
                Log.e(TAG, "Could not start AerialViews", e);
            }
        }
    }
}
