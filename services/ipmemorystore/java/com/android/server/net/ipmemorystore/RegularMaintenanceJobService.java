/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.net.ipmemorystore;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.ipmemorystore.IOnStatusListener;
import android.net.ipmemorystore.Status;
import android.net.ipmemorystore.StatusParcelable;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Regular maintenance job service.
 * @hide
 */
public final class RegularMaintenanceJobService extends JobService {
    // Must be unique within the system server uid.
    public static final int REGULAR_MAINTENANCE_ID = 3345678;

    /**
     * Class for interrupt check of maintenance job.
     */
    public static final class InterruptMaintenance {
        volatile boolean mInterrupt;

        public InterruptMaintenance(boolean interrupt) {
            this.mInterrupt = interrupt;
        }

        public void setInterrupt(boolean interrupt) {
            mInterrupt = interrupt;
        }

        public boolean isInterrupt() {
            return mInterrupt;
        }
    }

    private final Map<Integer, InterruptMaintenance> mInterruptList = new HashMap<>();

    @Override
    public boolean onStartJob(JobParameters params) {
        final IpMemoryStoreService ipmss =
                (IpMemoryStoreService) ServiceManager.getService(Context.IP_MEMORY_STORE_SERVICE);
        final InterruptMaintenance im = new InterruptMaintenance(false);
        mInterruptList.put(params.getJobId(), im);
        ipmss.fullMaintenance(new IOnStatusListener() {
            @Override
            public void onComplete(final StatusParcelable statusParcelable) throws RemoteException {
                final Status result = new Status(statusParcelable);
                if (!result.isSuccess()) {
                    Log.e("RegularMaintenanceJobService", "Regular maintenance failed."
                            + " Error is " + result.toString());
                }
                mInterruptList.remove(params.getJobId());
                jobFinished(params, !result.isSuccess());
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        }, im);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        final InterruptMaintenance im = mInterruptList.get(params.getJobId());
        if (im != null) {
            Log.d("honda", "im=" + im);
            im.setInterrupt(true);
            Log.d("honda", "im=" + im);
            mInterruptList.remove(params.getJobId());
        }
        return true;
    }

    /** Schedule regular maintenance job */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        final ComponentName maintenanceJobName =
                new ComponentName(context, RegularMaintenanceJobService.class);

        // Regular maintenance is scheduled for when the device is idle with access power and a
        // minimum interval of one day.
        final JobInfo regularMaintenanceJob =
                new JobInfo.Builder(REGULAR_MAINTENANCE_ID, maintenanceJobName)
                        .setRequiresDeviceIdle(true)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setPeriodic(TimeUnit.HOURS.toMillis(24)).build();

        jobScheduler.schedule(regularMaintenanceJob);
    }

    /** Unschedule regular maintenance job */
    public static void unschedule(Context context) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(REGULAR_MAINTENANCE_ID);
    }
}
