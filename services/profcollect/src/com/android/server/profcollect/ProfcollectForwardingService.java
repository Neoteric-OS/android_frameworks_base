/**
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.profcollect;

import static android.content.Intent.ACTION_BATTERY_LOW;
import static android.content.Intent.ACTION_BATTERY_OKAY;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;
import com.android.server.IoThread;
import com.android.server.LocalManagerRegistry;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.art.ArtManagerLocal;
import com.android.server.profcollect.Utils;
import com.android.server.wm.ActivityMetricsLaunchObserver;
import com.android.server.wm.ActivityMetricsLaunchObserverRegistry;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IProfcollectUploader;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System-server-local proxy into the {@code IProfcollectd} native service.
 */
public final class ProfcollectForwardingService extends SystemService {
    public static final String LOG_TAG = "ProfcollectForwardingService";

    private static final String INTENT_UPLOAD_PROFILES =
            "com.android.server.profcollect.UPLOAD_PROFILES";
    private static final long BG_PROCESS_INTERVAL = TimeUnit.HOURS.toMillis(4); // every 4 hours.

    private int mUsageSetting;
    private boolean mUploadEnabled;

    static boolean sVerityEnforced;
    static boolean sIsInteractive;
    static boolean sAdbActive;
    static boolean sIsBatteryLow;

    private static volatile IProfCollectd sIProfcollect;
    private static volatile ProfcollectForwardingService sSelfService;
    private final Handler mHandler = new ProfcollectdHandler(IoThread.getHandler().getLooper());

    private IProviderStatusCallback mProviderStatusCallback = new IProviderStatusCallback.Stub() {
        public void onProviderReady() {
            mHandler.sendEmptyMessage(ProfcollectdHandler.MESSAGE_REGISTER_SCHEDULERS);
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BATTERY_LOW.equals(intent.getAction())) {
                sIsBatteryLow = true;
            } else if (ACTION_BATTERY_OKAY.equals(intent.getAction())) {
                sIsBatteryLow = false;
            } else if (ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(LOG_TAG, "Received broadcast that the device became interactive, was "
                        + sIsInteractive);
                sIsInteractive = true;
            } else if (ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Log.d(LOG_TAG, "Received broadcast that the device became noninteractive, was "
                        + sIsInteractive);
                sIsInteractive = false;
            } else if (INTENT_UPLOAD_PROFILES.equals(intent.getAction())) {
                Log.d(LOG_TAG, "Received broadcast to pack and upload reports");
                createAndUploadReport(sSelfService);
            } else if (UsbManager.ACTION_USB_STATE.equals(intent.getAction())) {
                boolean isADB = intent.getBooleanExtra(UsbManager.USB_FUNCTION_ADB, false);
                if (isADB) {
                    boolean connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                    Log.d(LOG_TAG, "Received broadcast that ADB became " + connected
                            + ", was " + sAdbActive);
                    sAdbActive = connected;
                }
            }
        }
    };

    public ProfcollectForwardingService(Context context) {
        super(context);

        if (sSelfService != null) {
            throw new AssertionError("only one service instance allowed");
        }
        sSelfService = this;

        // Get "Usage & diagnostics" checkbox status. 1 is for enabled, 0 is for disabled.
        try {
            mUsageSetting = Settings.Global.getInt(context.getContentResolver(), "multi_cb");
        } catch (SettingNotFoundException e) {
            Log.e(LOG_TAG, "Usage setting not found: " + e.getMessage());
            mUsageSetting = -1;
        }

        // Check verity, disable profile upload if not enforced.
        final String verityMode = SystemProperties.get("ro.boot.veritymode");
        sVerityEnforced = verityMode.equals("enforcing");
        if (!sVerityEnforced) {
            Log.d(LOG_TAG, "verity is not enforced: " + verityMode);
        }

        mUploadEnabled =
            context.getResources().getBoolean(R.bool.config_profcollectReportUploaderEnabled);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BATTERY_LOW);
        filter.addAction(ACTION_BATTERY_OKAY);
        filter.addAction(ACTION_SCREEN_ON);
        filter.addAction(ACTION_SCREEN_OFF);
        filter.addAction(INTENT_UPLOAD_PROFILES);
        filter.addAction(UsbManager.ACTION_USB_STATE);
        context.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Check whether profcollect is enabled through device config.
     */
    public static boolean enabled() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PROFCOLLECT_NATIVE_BOOT, "enabled",
            false) || SystemProperties.getBoolean("persist.profcollectd.enabled_override", false);
    }

    @Override
    public void onStart() {
        connectNativeService();
    }

    @Override
    @RequiresPermission(Manifest.permission.MANAGE_USB)
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            UsbManager usbManager = getContext().getSystemService(UsbManager.class);
            if (usbManager == null) {
                sAdbActive = false;
                Log.d(LOG_TAG, "USBManager is not ready");
            } else {
                sAdbActive = ((usbManager.getCurrentFunctions() & UsbManager.FUNCTION_ADB) == 1);
                Log.d(LOG_TAG, "ADB is " + sAdbActive + " on system startup");
            }

            PowerManager powerManager = getContext().getSystemService(PowerManager.class);
            if (powerManager == null) {
                sIsInteractive = true;
                Log.d(LOG_TAG, "PowerManager is not ready");
            } else {
                sIsInteractive = powerManager.isInteractive();
                Log.d(LOG_TAG, "Device is interactive " + sIsInteractive + " on system startup");
            }
        }
        if (phase == PHASE_BOOT_COMPLETED) {
            IProfCollectd profcollect = sIProfcollect;
            if (profcollect == null) {
                return;
            }
            BackgroundThread.get().getThreadHandler().post(() -> {
                if (serviceHasSupportedTraceProvider()) {
                    registerProviderStatusCallback();
                }
            });
        }
    }

    private void registerProviderStatusCallback() {
        IProfCollectd profcollect = sIProfcollect;
        if (profcollect == null) {
            return;
        }
        try {
            profcollect.registerProviderStatusCallback(mProviderStatusCallback);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to register provider status callback: " + e.getMessage());
        }
    }

    private boolean serviceHasSupportedTraceProvider() {
        IProfCollectd profcollect = sIProfcollect;
        if (profcollect == null) {
            return false;
        }
        try {
            return !profcollect.get_supported_provider().isEmpty();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to get supported provider: " + e.getMessage());
            return false;
        }
    }

    private boolean tryConnectNativeService() {
        if (connectNativeService()) {
            return true;
        }
        // Cannot connect to the native service at this time, retry after a short delay.
        mHandler.sendEmptyMessageDelayed(ProfcollectdHandler.MESSAGE_BINDER_CONNECT, 5000);
        return false;
    }

    private boolean connectNativeService() {
        try {
            IProfCollectd profcollectd =
                    IProfCollectd.Stub.asInterface(
                            ServiceManager.getServiceOrThrow("profcollectd"));
            profcollectd.asBinder().linkToDeath(new ProfcollectdDeathRecipient(), /*flags*/0);
            sIProfcollect = profcollectd;
            return true;
        } catch (ServiceManager.ServiceNotFoundException | RemoteException e) {
            Log.w(LOG_TAG, "Failed to connect profcollectd binder service.");
            return false;
        }
    }

    private class ProfcollectdHandler extends Handler {
        public ProfcollectdHandler(Looper looper) {
            super(looper);
        }

        public static final int MESSAGE_BINDER_CONNECT = 0;
        public static final int MESSAGE_REGISTER_SCHEDULERS = 1;

        @Override
        public void handleMessage(android.os.Message message) {
            switch (message.what) {
                case MESSAGE_BINDER_CONNECT:
                    connectNativeService();
                    break;
                case MESSAGE_REGISTER_SCHEDULERS:
                    registerObservers();
                    PeriodicTraceJobService.schedule(getContext());
                    ReportProcessJobService.schedule(getContext());
                    break;
                default:
                    throw new AssertionError("Unknown message: " + message);
            }
        }
    }

    private class ProfcollectdDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            Log.w(LOG_TAG, "profcollectd has died");

            sIProfcollect = null;
            tryConnectNativeService();
        }
    }

    /**
     * Background report process and upload service.
     */
    public static class PeriodicTraceJobService extends JobService {
        // Unique ID in system server
        private static final int PERIODIC_TRACE_JOB_ID = 241207;
        private static final ComponentName JOB_SERVICE_NAME = new ComponentName(
                "android",
                PeriodicTraceJobService.class.getName());

        /**
         * Attach the service to the system job scheduler.
         */
        public static void schedule(Context context) {
            final int interval = DeviceConfig.getInt(DeviceConfig.NAMESPACE_PROFCOLLECT_NATIVE_BOOT,
                    "collection_interval", 600);
            JobScheduler js = context.getSystemService(JobScheduler.class);
            js.schedule(new JobInfo.Builder(PERIODIC_TRACE_JOB_ID, JOB_SERVICE_NAME)
                    .setPeriodic(TimeUnit.SECONDS.toMillis(interval))
                    // PRIORITY_DEFAULT is the highest priority we can request for a periodic job.
                    .setPriority(JobInfo.PRIORITY_DEFAULT)
                    .build());
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            IProfCollectd profcollect = sIProfcollect;
            if (profcollect != null) {
                Utils.traceSystem(profcollect, "periodic");
            }
            jobFinished(params, false);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return false;
        }
    }

    /**
     * Background report process and upload service.
     */
    public static class ReportProcessJobService extends JobService {
        // Unique ID in system server
        private static final int REPORT_PROCESS_JOB_ID = 260817;
        private static final ComponentName JOB_SERVICE_NAME = new ComponentName(
                "android",
                ReportProcessJobService.class.getName());

        /**
         * Attach the service to the system job scheduler.
         */
        public static void schedule(Context context) {
            JobScheduler js = context.getSystemService(JobScheduler.class);
            js.schedule(new JobInfo.Builder(REPORT_PROCESS_JOB_ID, JOB_SERVICE_NAME)
                    .setRequiresDeviceIdle(true)
                    .setRequiresCharging(true)
                    .setPeriodic(BG_PROCESS_INTERVAL)
                    .setPriority(JobInfo.PRIORITY_MIN)
                    .build());
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            createAndUploadReport(sSelfService);
            jobFinished(params, false);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return false;
        }
    }

    // Event observers
    private void registerObservers() {
        BackgroundThread.get().getThreadHandler().post(
                () -> {
                    registerAppLaunchObserver();
                    registerCameraOpenObserver();
                    registerDex2oatObserver();
                    registerOTAObserver();
                });
    }

    private final AppLaunchObserver mAppLaunchObserver = new AppLaunchObserver();
    private void registerAppLaunchObserver() {
        ActivityTaskManagerInternal atmInternal =
                LocalServices.getService(ActivityTaskManagerInternal.class);
        ActivityMetricsLaunchObserverRegistry launchObserverRegistry =
                atmInternal.getLaunchObserverRegistry();
        launchObserverRegistry.registerLaunchObserver(mAppLaunchObserver);
    }

    private class AppLaunchObserver extends ActivityMetricsLaunchObserver {
        @Override
        public void onIntentStarted(Intent intent, long timestampNanos) {
            if (Utils.withFrequency("applaunch_trace_freq", 5)) {
                IProfCollectd profcollect = sIProfcollect;
                if (profcollect != null) {
                    Utils.traceSystem(profcollect, "applaunch");
                }
            }
        }
    }

    private void registerDex2oatObserver() {
        ArtManagerLocal aml = LocalManagerRegistry.getManager(ArtManagerLocal.class);
        if (aml == null) {
            Log.w(LOG_TAG, "Couldn't get ArtManagerLocal");
            return;
        }
        aml.setBatchDexoptStartCallback(Runnable::run,
                (snapshot, reason, defaultPackages, builder, passedSignal) -> {
                    traceOnDex2oatStart();
                });
    }

    private void traceOnDex2oatStart() {
        if (Utils.withFrequency("dex2oat_trace_freq", 25)) {
            IProfCollectd profcollect = sIProfcollect;
            if (profcollect != null) {
                // Dex2oat could take a while before it starts. Add a short delay before start tracing.
                Utils.traceSystem(profcollect, "dex2oat", /* delayMs */ 1000);
            }
        }
    }

    private void registerOTAObserver() {
        UpdateEngine updateEngine = new UpdateEngine();
        updateEngine.bind(new UpdateEngineCallback() {
            @Override
            public void onStatusUpdate(int status, float percent) {
                if (status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT) {
                    createAndUploadReport(sSelfService);
                }
            }

            @Override
            public void onPayloadApplicationComplete(int errorCode) {
                // Ignored
            }
        });
    }

    private static void createAndUploadReport(ProfcollectForwardingService pfs) {
        if (pfs == null) {
            Log.e(LOG_TAG, "ProfcollectForwardingService is null; skipping report");
            return;
        }
        BackgroundThread.get().getThreadHandler().post(() -> {
            IProfCollectd profcollect = sIProfcollect;
            if (profcollect == null) {
                return;
            }
            String reportName;
            try {
                String reportUuid = profcollect.report(pfs.mUsageSetting);
                if (reportUuid == null || reportUuid.isEmpty()) {
                    Log.e(LOG_TAG, "Generated report UUID is null or empty; skipping upload");
                    return;
                }
                reportName = reportUuid + ".zip";
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to create report: " + e.getMessage());
                return;
            }
            if (!pfs.mUploadEnabled) {
                Log.i(LOG_TAG, "Upload is not enabled.");
                return;
            }
            if (!sVerityEnforced) {
                Log.i(LOG_TAG, "Verity is not enforced.");
                return;
            }
            if (Flags.enableProfcollectServiceUploader()) {
                uploadReportViaService(pfs, reportName);
            } else {
                fallbackToBroadcast(pfs, reportName);
            }
        });
    }

    private static void uploadReportViaService(ProfcollectForwardingService pfs, String reportName) {
        Context context = pfs.getContext();
        String uploaderPackage = context.getResources().getString(
                R.string.config_defaultProfcollectReportUploaderApp);
        String uploaderAction = context.getResources().getString(
                R.string.config_defaultProfcollectReportUploaderAction);

        if (uploaderPackage.isEmpty() || uploaderAction.isEmpty()) {
            Log.e(LOG_TAG, "Uploader package or action is empty; falling back to broadcast");
            fallbackToBroadcast(pfs, reportName);
            return;
        }

        // Verify package authenticity (must be platform-signed)
        if (context.getPackageManager().checkSignatures("android", uploaderPackage)
                != PackageManager.SIGNATURE_MATCH) {
            Log.e(LOG_TAG, "Uploader package " + uploaderPackage + " is not platform-signed.");
            fallbackToBroadcast(pfs, reportName);
            return;
        }

        Intent intent = new Intent(uploaderAction).setPackage(uploaderPackage);
        String uuid = UUID.randomUUID().toString();

        ServiceConnection conn = new ServiceConnection() {
            private final AtomicBoolean mBound = new AtomicBoolean(true);

            private void safeUnbind() {
                if (mBound.compareAndSet(true, false)) {
                    try {
                        context.unbindService(this);
                    } catch (IllegalArgumentException e) {
                        Log.w(LOG_TAG, "Service already unbound or not registered", e);
                    }
                }
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(LOG_TAG, "Connected to uploader service");
                IProfcollectUploader uploader = IProfcollectUploader.Stub.asInterface(service);
                
                ParcelFileDescriptor[] pipes = null;
                try {
                    pipes = ParcelFileDescriptor.createPipe();
                    ParcelFileDescriptor readFd = pipes[0];
                    ParcelFileDescriptor writeFd = pipes[1];

                    uploader.upload("PROFCOLLECT_PROFILE", readFd, uuid);
                    
                    // Close our copy of readFd immediately after passing it
                    try {
                        readFd.close();
                    } catch (IOException e) {
                        Log.w(LOG_TAG, "Failed to close readFd", e);
                    }

                    // Use a dedicated thread to avoid blocking BackgroundThread
                    Thread streamer = new Thread(() -> {
                        File reportFile = new File("/data/misc/profcollectd/report", reportName);
                        try (ParcelFileDescriptor pfd = writeFd;
                             FileInputStream fis = new FileInputStream(reportFile);
                             ParcelFileDescriptor.AutoCloseOutputStream fos = 
                                     new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                            Log.d(LOG_TAG, "Successfully streamed report: " + reportName);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "Failed to stream report: " + e.getMessage());
                        } finally {
                            safeUnbind();
                        }
                    }, "profcollect-streamer");

                    try {
                        streamer.start();
                    } catch (Throwable t) {
                        Log.e(LOG_TAG, "Failed to start streamer thread", t);
                        try {
                            writeFd.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        safeUnbind();
                    }

                } catch (RemoteException | IOException e) {
                    Log.e(LOG_TAG, "Failed to upload report via service: " + e.getMessage());
                    if (pipes != null) {
                        for (ParcelFileDescriptor pfd : pipes) {
                            if (pfd != null) {
                                try {
                                    pfd.close();
                                } catch (IOException ex) {
                                    // Ignore
                                }
                            }
                        }
                    }
                    safeUnbind();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(LOG_TAG, "Disconnected from uploader service");
            }

            @Override
            public void onNullBinding(ComponentName name) {
                Log.e(LOG_TAG, "Null binding received from uploader service");
                safeUnbind();
                fallbackToBroadcast(pfs, reportName);
            }

            @Override
            public void onBindingDied(ComponentName name) {
                Log.w(LOG_TAG, "Binding died for uploader service");
                safeUnbind();
            }
        };

        if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to bind to uploader service; falling back to broadcast");
            fallbackToBroadcast(pfs, reportName);
        }
    }

    private static void fallbackToBroadcast(ProfcollectForwardingService pfs, String reportName) {
        Intent intent = new Intent()
                .setPackage("com.android.shell")
                .setAction("com.android.shell.action.PROFCOLLECT_UPLOAD")
                .putExtra("filename", reportName);
        pfs.getContext().sendBroadcast(intent);
    }

    private void registerCameraOpenObserver() {
        CameraManager cm = getContext().getSystemService(CameraManager.class);
        cm.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraOpened(String cameraId, String packageId) {
                Log.d(LOG_TAG, "Received camera open event from: " + packageId);
                // Skip face auth since it triggers way too often.
                if (packageId.startsWith("client.pid")) {
                    return;
                }
                // Additional vendor specific list of apps to skip.
                String[] cameraSkipPackages =
                    getContext().getResources().getStringArray(
                        R.array.config_profcollectOnCameraOpenedSkipPackages);
                if (Arrays.asList(cameraSkipPackages).contains(packageId)) {
                    return;
                }
                if (Utils.withFrequency("camera_trace_freq", 10)) {
                    IProfCollectd profcollect = sIProfcollect;
                    if (profcollect != null) {
                        Utils.traceProcess(profcollect,
                                "camera",
                                "android.hardware.camera.provider",
                                /* durationMs */ 5000);
                    }
                }
            }
        }, null);
    }
}
