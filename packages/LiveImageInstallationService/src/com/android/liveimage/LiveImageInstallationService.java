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

package com.android.liveimage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.LiveImage;
import android.os.LiveImageManager;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static android.os.AsyncTask.Status.FINISHED;
import static android.os.AsyncTask.Status.PENDING;
import static android.os.AsyncTask.Status.RUNNING;
import static com.android.liveimage.InstallAsyncTask.RESULT_OK;
import static com.android.liveimage.InstallAsyncTask.RESULT_ERROR_IO;
import static com.android.liveimage.InstallAsyncTask.RESULT_ERROR_FILE_NOT_FOUND;
import static com.android.liveimage.InstallAsyncTask.RESULT_ERROR_UNSUPPORTED_IMAGE_SOURCE;
import static com.android.liveimage.InstallAsyncTask.RESULT_ERROR_NETWORK;
import static com.android.liveimage.InstallAsyncTask.RESULT_ERROR_EXCEPTION;
import static android.os.LiveImageManager.CAUSE_NOT_SPECIFIED;
import static android.os.LiveImageManager.CAUSE_INSTALL_COMPLETED;
import static android.os.LiveImageManager.CAUSE_INSTALL_CANCELLED;
import static android.os.LiveImageManager.CAUSE_ERROR_FILE_NOT_FOUND;
import static android.os.LiveImageManager.CAUSE_ERROR_IO;
import static android.os.LiveImageManager.CAUSE_ERROR_NETWORK;
import static android.os.LiveImageManager.CAUSE_ERROR_UNSUPPORTED_IMAGE_SOURCE;
import static android.os.LiveImageManager.CAUSE_ERROR_EXCEPTION;
import static android.os.LiveImageManager.STATUS_UNINIT;
import static android.os.LiveImageManager.STATUS_IN_PROGRESS;
import static android.os.LiveImageManager.STATUS_READY;
import static android.os.LiveImageManager.STATUS_IN_USE;


public class LiveImageInstallationService extends Service
        implements InstallAsyncTask.InstallStatusListener {

    private static final String TAG = "LiveImageInstallationService";

    /*
     * Intent actions
     */
    // TODO: use android.content.Intent.ACTION_INSTALL_LIVEIMAGE
    private static final String ACTION_START_INSTALL =
            Intent.ACTION_INSTALL_LIVEIMAGE;
    private static final String ACTION_CANCEL_INSTALL =
            "com.android.liveimage.ACTION_CANCEL_INSTALL";
    private static final String ACTION_REBOOT_TO_LIVEIMAGE =
            "com.android.liveimage.ACTION_REBOOT_TO_LIVEIMAGE";
    private static final String ACTION_REBOOT_TO_NORMAL =
            "com.android.liveimage.ACTION_REBOOT_TO_NORMAL";
    static final String ACTION_NOTIFY_IF_IS_IN_USE =
            "com.android.liveimage.ACTION_NOTIFY_IF_IS_IN_USE";


    /*
     * For notification
     */
    private static final String NOTIFICATION_CHANNEL_ID = "com.android.liveimage";
    private static final String NOTIFICATION_CHANNEL_NAME = "LiveImage Service";
    private static final int NOTIFICATION_ID = 1;

    /*
     * IPC
     */
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<>();

    /** Handler of incoming messages from clients. */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    static class IncomingHandler extends Handler {
        private final WeakReference<LiveImageInstallationService> mWeakService;

        IncomingHandler(LiveImageInstallationService service) {
            mWeakService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            LiveImageInstallationService service = mWeakService.get();

            if (service != null) {
                service.handleMessage(msg);
            }
        }
    }

    private InstallAsyncTask mInstallTask;
    private long mSystemSize;
    private long mInstalledSize;
    private boolean mJustCancelledByUser;

    // Notification
    private NotificationManager mNM;

    private PendingIntent mPiCancel;
    private PendingIntent mPiRebootToLiveImage;
    private PendingIntent mPiRebootToNormal;

    // LiveImage platform service
    private LiveImage mLiveImage;


    @Override
    public void onCreate() {
        super.onCreate();

        prepareNotification();

        mLiveImage = new LiveImage();
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (ACTION_START_INSTALL.equals(action)) {
            executeInstallCommand(intent);
        } else if (ACTION_CANCEL_INSTALL.equals(action)) {
            executeCancelCommand();
        } else if (ACTION_REBOOT_TO_LIVEIMAGE.equals(action)) {
            executeRebootToLiveImageCommand();
        } else if (ACTION_REBOOT_TO_NORMAL.equals(action)) {
            executeRebootToNormalCommand();
        } else if (ACTION_NOTIFY_IF_IS_IN_USE.equals(action)) {
            executeNotifyIfIsInUseCommand();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onProgressUpdate(long installedSize) {
        mInstalledSize = installedSize;
        postStatus(STATUS_IN_PROGRESS, CAUSE_NOT_SPECIFIED);
    }

    @Override
    public void onResult(int result) {
        if (result == RESULT_OK) {
            postStatus(STATUS_READY, CAUSE_INSTALL_COMPLETED);
            return;
        }

        // if it's not successful, reset the task and stop self.
        resetTaskAndStop();

        switch (result) {
            case RESULT_ERROR_FILE_NOT_FOUND:
                postStatus(STATUS_UNINIT, CAUSE_ERROR_FILE_NOT_FOUND);
                break;

            case RESULT_ERROR_IO:
                postStatus(STATUS_UNINIT, CAUSE_ERROR_IO);
                break;

            case RESULT_ERROR_NETWORK:
                postStatus(STATUS_UNINIT, CAUSE_ERROR_NETWORK);
                break;

            case RESULT_ERROR_UNSUPPORTED_IMAGE_SOURCE:
                postStatus(STATUS_UNINIT, CAUSE_ERROR_UNSUPPORTED_IMAGE_SOURCE);
                break;

            case RESULT_ERROR_EXCEPTION:
                postStatus(STATUS_UNINIT, CAUSE_ERROR_EXCEPTION);
                break;
        }
    }

    @Override
    public void onCancelled() {
        resetTaskAndStop();
        postStatus(STATUS_UNINIT, CAUSE_INSTALL_CANCELLED);
    }

    private void executeInstallCommand(Intent intent) {
        if (!verifyRequest(intent)) {
            Log.e(TAG, "Verification failed. Please use VerificationActivity");
            return;
        }

        if (mInstallTask != null) {
            Log.e(TAG, "There is already an install task running");
            return;
        }

        String url = intent.getDataString();
        mSystemSize = intent.getLongExtra(LiveImageManager.KEY_SYSTEM_SIZE, 0);
        long userdata = intent.getLongExtra(LiveImageManager.KEY_USERDATA_SIZE, 0);

        mInstallTask = new InstallAsyncTask(url, mSystemSize, userdata, mLiveImage, this);
        mInstallTask.execute();

        // start fore ground
        startForeground(NOTIFICATION_ID,
                buildNotification(STATUS_IN_PROGRESS, CAUSE_NOT_SPECIFIED));
    }

    private void executeCancelCommand() {
        if (mInstallTask == null || mInstallTask.getStatus() == PENDING) {
            // this should not happen in our designed flow, just in case
            Log.e(TAG, "Cancel command triggered, but there is no task running");
            mNM.cancel(NOTIFICATION_ID);

            return;
        }

        mJustCancelledByUser = true;

        if (mInstallTask.cancel(false)) {
            // Will cleanup and post status in onCancelled()
            Log.d(TAG, "Cancel request filed successfully");
        } else {
            Log.d(TAG, "Requested cancel, completed task will be discarded");

            resetTaskAndStop();
            postStatus(STATUS_UNINIT, CAUSE_INSTALL_CANCELLED);
        }

    }

    private void executeRebootToLiveImageCommand() {
        mLiveImage.commit();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (powerManager != null) {
            powerManager.reboot("liveimage");
        }
    }

    private void executeRebootToNormalCommand() {
        mLiveImage.remove();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (powerManager != null) {
            powerManager.reboot(null);
        }
    }

    private void executeNotifyIfIsInUseCommand() {
        if (isInLiveImage()) {
            startForeground(NOTIFICATION_ID,
                    buildNotification(STATUS_IN_USE, CAUSE_NOT_SPECIFIED));
        }
    }

    private void resetTaskAndStop() {
        mInstallTask = null;

        stopForeground(true);

        // stop self, but this service is not destroyed yet if it's still bound
        stopSelf();
    }

    private void prepareNotification() {
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (mNM != null) {
            mNM.createNotificationChannel(chan);
        }

        Intent intentCancel = new Intent(this, LiveImageInstallationService.class);
        intentCancel.setAction(ACTION_CANCEL_INSTALL);
        mPiCancel = PendingIntent.getService(this, 0, intentCancel, 0);

        Intent intentRebootToLiveImage = new Intent(this, LiveImageInstallationService.class);
        intentRebootToLiveImage.setAction(ACTION_REBOOT_TO_LIVEIMAGE);
        mPiRebootToLiveImage = PendingIntent.getService(this, 0, intentRebootToLiveImage, 0);

        Intent intentRebootToNormal = new Intent(this, LiveImageInstallationService.class);
        intentRebootToNormal.setAction(ACTION_REBOOT_TO_NORMAL);
        mPiRebootToNormal = PendingIntent.getService(this, 0, intentRebootToNormal, 0);
    }

    private Notification buildNotification(int status, int cause) {
        Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_update_googblue_24dp)
                .setProgress(0, 0, false);

        switch (status) {
            case STATUS_IN_PROGRESS:
                builder.setContentText(String.valueOf(mInstalledSize));

                int max = (int) Math.max(mSystemSize >> 20, 1);
                int progress = (int) mInstalledSize >> 20;

                builder.setProgress(max, progress, false);

                builder.addAction(new Notification.Action.Builder(
                        null, "Cancel", mPiCancel).build());

                break;

            case STATUS_READY:
                builder.setContentText("Install Completed");

                builder.addAction(new Notification.Action.Builder(
                        null, "Reboot to LiveImage", mPiRebootToLiveImage).build());

                builder.addAction(new Notification.Action.Builder(
                        null, "Cancel", mPiCancel).build());

                break;

            case STATUS_IN_USE:
                builder.setContentText("LiveImage Running");

                builder.addAction(new Notification.Action.Builder(
                        null, "Reboot to Normal", mPiRebootToNormal).build());

                break;

            case STATUS_UNINIT:
                if (cause != CAUSE_INSTALL_CANCELLED) {
                    builder.setContentText("Install Failed");
                } else {
                    Log.e(TAG, "Should not notify user if the task is cancelled, or hasn't run");
                }
                break;

            default:
                Log.e(TAG, "Something wrong");
        }

        return builder.build();
    }

    private boolean verifyRequest(Intent intent) {
        String url = intent.getDataString();

        return VerificationActivity.isVerified(url);
    }

    private void postStatus(int status, int cause) {
        Log.d(TAG, "postStatus() " + status);

        boolean notifyOnNotificationBar = true;

        if (status == STATUS_UNINIT && cause == CAUSE_INSTALL_CANCELLED && mJustCancelledByUser) {
            // if task is cancelled by user, do not notify them
            notifyOnNotificationBar = false;
            mJustCancelledByUser = false;
        }

        if (notifyOnNotificationBar) {
            mNM.notify(NOTIFICATION_ID, buildNotification(status, cause));
        }


        Log.d(TAG, "mClients " + mClients.size());

        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                notifyOneClient(mClients.get(i), status, cause);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }

    private void notifyOneClient(Messenger client, int status, int cause) throws RemoteException {
        Bundle bundle = new Bundle();
        bundle.putLong(LiveImageManager.KEY_INSTALLED_SIZE, mInstalledSize);
        client.send(Message.obtain(null, LiveImageManager.MSG_POST_STATUS, status, cause, bundle));
    }

    private int getStatus() {
        if (isInLiveImage()) {
            return STATUS_IN_USE;

        } else if (mInstallTask == null) {
            return STATUS_UNINIT;

        }

        switch (mInstallTask.getStatus()) {
            case PENDING:
                return STATUS_UNINIT;

            case RUNNING:
                return STATUS_IN_PROGRESS;

            case FINISHED:
                int result = mInstallTask.getResult();

                if (result == RESULT_OK) {
                    return STATUS_READY;
                } else {
                    Log.e(TAG, "A failed AsyncTask should already be reset");
                    return STATUS_UNINIT;
                }

            default:
                return STATUS_UNINIT;
        }
    }

    private boolean isInLiveImage() {
        return mLiveImage.isInUse();
    }

    void handleMessage(Message msg) {
        switch (msg.what) {
            case LiveImageManager.MSG_REGISTER_LISTENER:
                try {
                    Messenger client = msg.replyTo;

                    int status = getStatus();

                    // tell just registered client my status, but do not specify cause
                    notifyOneClient(client, status, CAUSE_NOT_SPECIFIED);

                    mClients.add(client);
                } catch (RemoteException e) {
                    // do nothing if we cannot send update to the client
                    e.printStackTrace();
                }

                break;
            case LiveImageManager.MSG_UNREGISTER_LISTENER:
                mClients.remove(msg.replyTo);
                break;
            default:
                // do nothing
        }
    }
}
