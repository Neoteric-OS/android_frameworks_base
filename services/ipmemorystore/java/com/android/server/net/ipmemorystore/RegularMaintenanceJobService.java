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

import com.android.server.LocalServices;

import java.util.concurrent.TimeUnit;

/**
 * Regular maintenance job service.
 * @hide
 */
public final class RegularMaintenanceJobService extends JobService {
    // Must be unique within the system server uid.
    public static final int REGULAR_MAINTENANCE_ID = 3345678;

    @Override
    public boolean onStartJob(JobParameters params) {
        final IpMemoryStoreServiceInternal ipmssi =
                LocalServices.getService(IpMemoryStoreServiceInternal.class);
        ipmssi.fullMaintenance();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /** Schedules regular maintenace job */
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

    /** Unschedules regular maintenace job */
    public static void unschedule(Context context) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(REGULAR_MAINTENANCE_ID);
    }
}
