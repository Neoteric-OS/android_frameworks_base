/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.recoverysystem;

import android.content.Context;
import android.content.IntentSender;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.IRecoverySystem;
import android.os.IRecoverySystemProgressListener;
import android.os.PowerManager;
import android.os.Process;
import android.os.RecoverySystem;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockSettingsInternal;
import com.android.internal.widget.RebootEscrowListener;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import libcore.io.IoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recovery system service is responsible for coordinating recovery related
 * functions on the device. It sets up (or clears) the bootloader control block
 * (BCB), which will be read by the bootloader and the recovery image. It also
 * triggers /system/bin/uncrypt via init to de-encrypt an OTA package on the
 * /data partition so that it can be accessed under the recovery image.
 */
public class RecoverySystemService extends IRecoverySystem.Stub implements RebootEscrowListener {
    private static final String TAG = "RecoverySystemService";
    private static final boolean DEBUG = false;

    // The socket at /dev/socket/uncrypt to communicate with uncrypt.
    private static final String UNCRYPT_SOCKET = "uncrypt";

    // The init services that communicate with /system/bin/uncrypt.
    @VisibleForTesting
    static final String INIT_SERVICE_UNCRYPT = "init.svc.uncrypt";
    @VisibleForTesting
    static final String INIT_SERVICE_SETUP_BCB = "init.svc.setup-bcb";
    @VisibleForTesting
    static final String INIT_SERVICE_CLEAR_BCB = "init.svc.clear-bcb";

    private static final Object sRequestLock = new Object();

    private static final int SOCKET_CONNECTION_MAX_RETRY = 30;

    private final Injector mInjector;
    private final Context mContext;

    private final Map<String, IntentSender> mCallerPendingRequest = new HashMap<>();
    private final Set<String> mCallerPreparedForReboot = new HashSet<>();

    private static class ResumeOnRebootRequestResult {
        private final boolean mNeedPreparation;
        private final boolean mSendIntent;

        ResumeOnRebootRequestResult(boolean needPreparation, boolean sendIntent) {
            if (needPreparation && sendIntent) {
                throw new IllegalStateException("Reboot escrow preparation should not be requested "
                        + "together with sending intent.");
            }

            mNeedPreparation = needPreparation;
            mSendIntent = sendIntent;
        }
    }

    private static class ResumeOnRebootClearResult {
        private final boolean mRequested;
        private final boolean mNeedClear;

        ResumeOnRebootClearResult(boolean requested, boolean needClear) {
            mRequested = requested;
            mNeedClear = needClear;
        }
    }

    static class Injector {
        protected final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        public Context getContext() {
            return mContext;
        }

        public LockSettingsInternal getLockSettingsService() {
            return LocalServices.getService(LockSettingsInternal.class);
        }

        public PowerManager getPowerManager() {
            return (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        }

        public String systemPropertiesGet(String key) {
            return SystemProperties.get(key);
        }

        public void systemPropertiesSet(String key, String value) {
            SystemProperties.set(key, value);
        }

        public boolean uncryptPackageFileDelete() {
            return RecoverySystem.UNCRYPT_PACKAGE_FILE.delete();
        }

        public String getUncryptPackageFileName() {
            return RecoverySystem.UNCRYPT_PACKAGE_FILE.getName();
        }

        public FileWriter getUncryptPackageFileWriter() throws IOException {
            return new FileWriter(RecoverySystem.UNCRYPT_PACKAGE_FILE);
        }

        public UncryptSocket connectService() {
            UncryptSocket socket = new UncryptSocket();
            if (!socket.connectService()) {
                socket.close();
                return null;
            }
            return socket;
        }

        public void threadSleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

    /**
     * Handles the lifecycle events for the RecoverySystemService.
     */
    public static final class Lifecycle extends SystemService {
        private RecoverySystemService mRecoverySystemService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
                mRecoverySystemService.onSystemServicesReady();
            }
        }

        @Override
        public void onStart() {
            mRecoverySystemService = new RecoverySystemService(getContext());
            publishBinderService(Context.RECOVERY_SERVICE, mRecoverySystemService);
        }
    }

    private RecoverySystemService(Context context) {
        this(new Injector(context));
    }

    @VisibleForTesting
    RecoverySystemService(Injector injector) {
        mInjector = injector;
        mContext = injector.getContext();
    }

    @VisibleForTesting
    void onSystemServicesReady() {
        mInjector.getLockSettingsService().setRebootEscrowListener(this);
    }

    @Override // Binder call
    public boolean uncrypt(String filename, IRecoverySystemProgressListener listener) {
        if (DEBUG) Slog.d(TAG, "uncrypt: " + filename);

        synchronized (sRequestLock) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);

            if (!checkAndWaitForUncryptService()) {
                Slog.e(TAG, "uncrypt service is unavailable.");
                return false;
            }

            // Write the filename into uncrypt package file to be read by
            // uncrypt.
            mInjector.uncryptPackageFileDelete();

            try (FileWriter uncryptFile = mInjector.getUncryptPackageFileWriter()) {
                uncryptFile.write(filename + "\n");
            } catch (IOException e) {
                Slog.e(TAG, "IOException when writing \""
                        + mInjector.getUncryptPackageFileName() + "\":", e);
                return false;
            }

            // Trigger uncrypt via init.
            mInjector.systemPropertiesSet("ctl.start", "uncrypt");

            // Connect to the uncrypt service socket.
            UncryptSocket socket = mInjector.connectService();
            if (socket == null) {
                Slog.e(TAG, "Failed to connect to uncrypt socket");
                return false;
            }

            // Read the status from the socket.
            try {
                int lastStatus = Integer.MIN_VALUE;
                while (true) {
                    int status = socket.getPercentageUncrypted();
                    // Avoid flooding the log with the same message.
                    if (status == lastStatus && lastStatus != Integer.MIN_VALUE) {
                        continue;
                    }
                    lastStatus = status;

                    if (status >= 0 && status <= 100) {
                        // Update status
                        Slog.i(TAG, "uncrypt read status: " + status);
                        if (listener != null) {
                            try {
                                listener.onProgress(status);
                            } catch (RemoteException ignored) {
                                Slog.w(TAG, "RemoteException when posting progress");
                            }
                        }
                        if (status == 100) {
                            Slog.i(TAG, "uncrypt successfully finished.");
                            // Ack receipt of the final status code. uncrypt
                            // waits for the ack so the socket won't be
                            // destroyed before we receive the code.
                            socket.sendAck();
                            break;
                        }
                    } else {
                        // Error in /system/bin/uncrypt.
                        Slog.e(TAG, "uncrypt failed with status: " + status);
                        // Ack receipt of the final status code. uncrypt waits
                        // for the ack so the socket won't be destroyed before
                        // we receive the code.
                        socket.sendAck();
                        return false;
                    }
                }
            } catch (IOException e) {
                Slog.e(TAG, "IOException when reading status: ", e);
                return false;
            } finally {
                socket.close();
            }

            return true;
        }
    }

    @Override // Binder call
    public boolean clearBcb() {
        if (DEBUG) Slog.d(TAG, "clearBcb");
        synchronized (sRequestLock) {
            return setupOrClearBcb(false, null);
        }
    }

    @Override // Binder call
    public boolean setupBcb(String command) {
        if (DEBUG) Slog.d(TAG, "setupBcb: [" + command + "]");
        synchronized (sRequestLock) {
            return setupOrClearBcb(true, command);
        }
    }

    @Override // Binder call
    public void rebootRecoveryWithCommand(String command) {
        if (DEBUG) Slog.d(TAG, "rebootRecoveryWithCommand: [" + command + "]");
        synchronized (sRequestLock) {
            if (!setupOrClearBcb(true, command)) {
                return;
            }

            // Having set up the BCB, go ahead and reboot.
            PowerManager pm = mInjector.getPowerManager();
            pm.reboot(PowerManager.REBOOT_RECOVERY);
        }
    }

    @Override // Binder call
    public boolean requestLskf(String callerId, IntentSender intentSender) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);

        if (callerId == null) {
            Slog.w(TAG, "Missing callerId when requesting lskf.");
            return false;
        }

        ResumeOnRebootRequestResult requestResult = updateRoRPreparationStateOnNewRequest(
                callerId, intentSender);
        // We consider the preparation done if someone else has prepared.
        if (requestResult.mSendIntent) {
            sendPreparedForRebootIntentIfNeeded(intentSender);
            return true;
        }

        // No need to ask lock settings service to prepare again.
        if (!requestResult.mNeedPreparation) {
            return true;
        }

        final long origId = Binder.clearCallingIdentity();
        try {
            mInjector.getLockSettingsService().prepareRebootEscrow();
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        return true;
    }

    // Checks and updates the resume on reboot preparation state.
    private synchronized ResumeOnRebootRequestResult updateRoRPreparationStateOnNewRequest(
            String callerId, IntentSender intentSender) {
        if (!mCallerPreparedForReboot.isEmpty()) {
            if (!mCallerPendingRequest.isEmpty()) {
                String msg = String.format("RoR pending request isn't empty when reboot escrow has"
                                + " been prepared, prepared callers: %s, pending callers: %s",
                        String.join(",", mCallerPreparedForReboot),
                        String.join(",", mCallerPendingRequest.keySet()));
                throw new IllegalStateException(msg);
            }

            if (mCallerPreparedForReboot.contains(callerId)) {
                return new ResumeOnRebootRequestResult(false, false);
            }

            // Someone else has prepared. Consider the preparation done, and send back the intent.
            mCallerPreparedForReboot.add(callerId);
            return new ResumeOnRebootRequestResult(false, true);
        }

        boolean needPreparation = mCallerPendingRequest.isEmpty();
        if (mCallerPendingRequest.containsKey(callerId)) {
            Slog.i(TAG, "Duplicate RoR preparation request for " + callerId);
        }
        // Update the request with the new intentSender.
        mCallerPendingRequest.put(callerId, intentSender);
        return new ResumeOnRebootRequestResult(needPreparation, false);
    }

    @Override
    public void onPreparedForReboot(boolean ready) {
        if (!ready) {
            return;
        }

        List<IntentSender> pendingIntentSenders = getIntentSendersOnPreparedForReboot();
        for (IntentSender intentSender : pendingIntentSenders) {
            sendPreparedForRebootIntentIfNeeded(intentSender);
        }
    }

    private synchronized List<IntentSender> getIntentSendersOnPreparedForReboot() {
        if (!mCallerPreparedForReboot.isEmpty()) {
            Slog.w(TAG, "onPreparedForReboot called when some clients have prepared.");
        }

        List<IntentSender> pendingIntentSenders = new ArrayList<>();
        for (Map.Entry<String, IntentSender> entry : mCallerPendingRequest.entrySet()) {
            pendingIntentSenders.add(entry.getValue());
            mCallerPreparedForReboot.add(entry.getKey());
        }
        mCallerPendingRequest.clear();
        return pendingIntentSenders;
    }

    private void sendPreparedForRebootIntentIfNeeded(IntentSender intentSender) {
        if (intentSender != null) {
            try {
                intentSender.sendIntent(null, 0, null, null, null);
            } catch (IntentSender.SendIntentException e) {
                Slog.w(TAG, "Could not send intent for prepared reboot: " + e.getMessage());
            }
        }
    }

    @Override // Binder call
    public boolean clearLskf(String callerId) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);
        if (callerId == null) {
            Slog.w(TAG, "Missing callerId when clearing lskf.");
            return false;
        }

        ResumeOnRebootClearResult clearResult = updateRoRPreparationStateOnClear(callerId);
        if (!clearResult.mRequested) {
            Slog.w(TAG, "RoR clear called before preparation for caller " + callerId);
            return false;
        }
        // Another RoR caller exists, no need to clear reboot escrow.
        if (!clearResult.mNeedClear) {
            return true;
        }

        final long origId = Binder.clearCallingIdentity();
        try {
            mInjector.getLockSettingsService().clearRebootEscrow();
        } finally {
            Binder.restoreCallingIdentity(origId);
        }

        return true;
    }

    private synchronized ResumeOnRebootClearResult updateRoRPreparationStateOnClear(
            String callerId) {
        if (!mCallerPreparedForReboot.contains(callerId) && !mCallerPendingRequest.containsKey(
                callerId)) {
            Slog.w(TAG, callerId + " hasn't prepared for resume on reboot");
            return new ResumeOnRebootClearResult(false, false);
        }
        mCallerPendingRequest.remove(callerId);
        mCallerPreparedForReboot.remove(callerId);

        // Check if others have prepared ROR.
        boolean needClear = mCallerPendingRequest.isEmpty() && mCallerPreparedForReboot.isEmpty();
        return new ResumeOnRebootClearResult(true, needClear);
    }

    @Override // Binder call
    public boolean rebootWithLskf(String callerId, String reason, boolean slotSwitch) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);
        if (callerId == null) {
            Slog.w(TAG, "Missing callerId when rebooting with lskf.");
            return false;
        }
        if (!isLskfCaptured(callerId)) {
            return false;
        }

        // TODO(xunchang) check the slot to boot into, and fail the reboot upon slot mismatch.
        // TODO(xunchang) write the vbmeta digest along with the escrowKey before reboot.
        if (!mInjector.getLockSettingsService().armRebootEscrow()) {
            Slog.w(TAG, "Failure to escrow key for reboot");
            return false;
        }

        PowerManager pm = mInjector.getPowerManager();
        pm.reboot(reason);
        return true;
    }

    @Override // Binder call
    public synchronized boolean isLskfCaptured(String callerId) {
        if (!mCallerPreparedForReboot.contains(callerId)) {
            Slog.i(TAG, "Reboot requested before prepare completed for caller " + callerId);
            return false;
        }
        return true;
    }

    /**
     * Check if any of the init services is still running. If so, we cannot
     * start a new uncrypt/setup-bcb/clear-bcb service right away; otherwise
     * it may break the socket communication since init creates / deletes
     * the socket (/dev/socket/uncrypt) on service start / exit.
     */
    private boolean checkAndWaitForUncryptService() {
        for (int retry = 0; retry < SOCKET_CONNECTION_MAX_RETRY; retry++) {
            final String uncryptService = mInjector.systemPropertiesGet(INIT_SERVICE_UNCRYPT);
            final String setupBcbService = mInjector.systemPropertiesGet(INIT_SERVICE_SETUP_BCB);
            final String clearBcbService = mInjector.systemPropertiesGet(INIT_SERVICE_CLEAR_BCB);
            final boolean busy = "running".equals(uncryptService)
                    || "running".equals(setupBcbService) || "running".equals(clearBcbService);
            if (DEBUG) {
                Slog.i(TAG, "retry: " + retry + " busy: " + busy
                        + " uncrypt: [" + uncryptService + "]"
                        + " setupBcb: [" + setupBcbService + "]"
                        + " clearBcb: [" + clearBcbService + "]");
            }

            if (!busy) {
                return true;
            }

            try {
                mInjector.threadSleep(1000);
            } catch (InterruptedException e) {
                Slog.w(TAG, "Interrupted:", e);
            }
        }

        return false;
    }

    private boolean setupOrClearBcb(boolean isSetup, String command) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);

        final boolean available = checkAndWaitForUncryptService();
        if (!available) {
            Slog.e(TAG, "uncrypt service is unavailable.");
            return false;
        }

        if (isSetup) {
            mInjector.systemPropertiesSet("ctl.start", "setup-bcb");
        } else {
            mInjector.systemPropertiesSet("ctl.start", "clear-bcb");
        }

        // Connect to the uncrypt service socket.
        UncryptSocket socket = mInjector.connectService();
        if (socket == null) {
            Slog.e(TAG, "Failed to connect to uncrypt socket");
            return false;
        }

        try {
            // Send the BCB commands if it's to setup BCB.
            if (isSetup) {
                socket.sendCommand(command);
            }

            // Read the status from the socket.
            int status = socket.getPercentageUncrypted();

            // Ack receipt of the status code. uncrypt waits for the ack so
            // the socket won't be destroyed before we receive the code.
            socket.sendAck();

            if (status == 100) {
                Slog.i(TAG, "uncrypt " + (isSetup ? "setup" : "clear")
                        + " bcb successfully finished.");
            } else {
                // Error in /system/bin/uncrypt.
                Slog.e(TAG, "uncrypt failed with status: " + status);
                return false;
            }
        } catch (IOException e) {
            Slog.e(TAG, "IOException when communicating with uncrypt:", e);
            return false;
        } finally {
            socket.close();
        }

        return true;
    }

    /**
     * Provides a wrapper for the low-level details of framing packets sent to the uncrypt
     * socket.
     */
    public static class UncryptSocket {
        private LocalSocket mLocalSocket;
        private DataInputStream mInputStream;
        private DataOutputStream mOutputStream;

        /**
         * Attempt to connect to the uncrypt service. Connection will be retried for up to
         * {@link #SOCKET_CONNECTION_MAX_RETRY} times. If the connection is unsuccessful, the
         * socket will be closed. If the connection is successful, the connection must be closed
         * by the caller.
         *
         * @return true if connection was successful, false if unsuccessful
         */
        public boolean connectService() {
            mLocalSocket = new LocalSocket();
            boolean done = false;
            // The uncrypt socket will be created by init upon receiving the
            // service request. It may not be ready by this point. So we will
            // keep retrying until success or reaching timeout.
            for (int retry = 0; retry < SOCKET_CONNECTION_MAX_RETRY; retry++) {
                try {
                    mLocalSocket.connect(new LocalSocketAddress(UNCRYPT_SOCKET,
                            LocalSocketAddress.Namespace.RESERVED));
                    done = true;
                    break;
                } catch (IOException ignored) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Interrupted:", e);
                    }
                }
            }
            if (!done) {
                Slog.e(TAG, "Timed out connecting to uncrypt socket");
                close();
                return false;
            }

            try {
                mInputStream = new DataInputStream(mLocalSocket.getInputStream());
                mOutputStream = new DataOutputStream(mLocalSocket.getOutputStream());
            } catch (IOException e) {
                close();
                return false;
            }

            return true;
        }

        /**
         * Sends a command to the uncrypt service.
         *
         * @param command command to send to the uncrypt service
         * @throws IOException if there was an error writing to the socket
         */
        public void sendCommand(String command) throws IOException {
            byte[] cmdUtf8 = command.getBytes(StandardCharsets.UTF_8);
            mOutputStream.writeInt(cmdUtf8.length);
            mOutputStream.write(cmdUtf8, 0, cmdUtf8.length);
        }

        /**
         * Reads the status from the uncrypt service which is usually represented as a percentage.
         *
         * @return an integer representing the percentage completed
         * @throws IOException if there was an error reading the socket
         */
        public int getPercentageUncrypted() throws IOException {
            return mInputStream.readInt();
        }

        /**
         * Sends a confirmation to the uncrypt service.
         *
         * @throws IOException if there was an error writing to the socket
         */
        public void sendAck() throws IOException {
            mOutputStream.writeInt(0);
        }

        /**
         * Closes the socket and all underlying data streams.
         */
        public void close() {
            IoUtils.closeQuietly(mInputStream);
            IoUtils.closeQuietly(mOutputStream);
            IoUtils.closeQuietly(mLocalSocket);
        }
    }

    private boolean isCallerShell() {
        final int callingUid = Binder.getCallingUid();
        return callingUid == Process.SHELL_UID || callingUid == Process.ROOT_UID;
    }

    private void enforceShell() {
        if (!isCallerShell()) {
            throw new SecurityException("Caller must be shell");
        }
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        enforceShell();
        final long origId = Binder.clearCallingIdentity();
        try {
            new RecoverySystemShellCommand(this).exec(
                    this, in, out, err, args, callback, resultReceiver);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }
}
