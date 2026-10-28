package com.vgs.newstv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Launcher gửi tín hiệu này sau khi cài app để app tạo kênh/thẻ ban đầu. Chỉ lên lịch, không làm việc nặng. */
public class InitializeProgramsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ChannelHelper.setup(context);
    }
}
