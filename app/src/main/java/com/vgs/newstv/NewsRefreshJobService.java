package com.vgs.newstv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/** Làm mới hàng thẻ tin: định kỳ 30 phút (JobScheduler có sẵn của Android) và 1 lần ngay khi cần. */
public class NewsRefreshJobService extends JobService {

    private static final String TAG = "NewsRefresh";
    private static final int JOB_PERIODIC = 7101;
    private static final int JOB_NOW = 7102;
    private static final long PERIOD_MS = 30L * 60 * 1000;

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = ChannelHelper.refresh(getApplicationContext());
            } catch (Throwable t) {
                Log.w(TAG, "Tác vụ lỗi", t);
            }
            jobFinished(params, !ok);   // lỗi mạng => hệ thống thử lại sau
        }, "news-refresh").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    static void schedulePeriodic(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        for (JobInfo ji : js.getAllPendingJobs()) {
            if (ji.getId() == JOB_PERIODIC) return;   // đã có lịch
        }
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, NewsRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .build());
    }

    static void scheduleNow(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(ctx, NewsRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(2000)
                .build());
    }
}
