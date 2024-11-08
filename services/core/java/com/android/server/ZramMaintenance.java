package com.android.server;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.IMmd;
import android.util.Slog;

import java.time.Duration;

public class ZramMaintenance extends JobService {
    private static final String TAG = ZramMaintenance.class.getName();
    private static final ComponentName sZramMaintenance =
            new ComponentName("android", ZramMaintenance.class.getName());

    @Override
    public boolean onStartJob(JobParameters params) {
        IBinder binder = ServiceManager.getService("mmd");
        Duration delay = Duration.ofDays(1);
        if (binder != null) {
            IMmd mmd = IMmd.Stub.asInterface(binder);
            try {
                long nextDelaySeconds = mmd.doZramMaintenance();
                delay = Duration.ofSeconds(nextDelaySeconds);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to doZramMaintenance", e);
            }
        } else {
            Slog.w(TAG, "binder not found");
            // TODO: this is for debugging
            delay = Duration.ofSeconds(1);
        }
        scheduleZramMaintenance(this, delay);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The thread that triggers the writeback is non-interruptible
        return false;
    }

    public static void scheduleZramMaintenance(Context context, Duration delay) {
        Slog.i(TAG, "scheduleZramMaintenance");
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        js.schedule(new JobInfo.Builder(0, sZramMaintenance)
                .setMinimumLatency(delay.toMillis())
                // TODO: make this true
                .setRequiresDeviceIdle(false)
                .build());
    }
}
