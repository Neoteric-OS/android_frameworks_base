/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.iris;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_IRIS;
import static android.Manifest.permission.RESET_IRIS_LOCKOUT;
import static android.Manifest.permission.USE_IRIS;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.SynchronousUserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.hardware.biometrics.iris.V1_0.IBiometricsIris;
import android.hardware.biometrics.iris.V1_0.IBiometricsIrisClientCallback;
import android.hardware.iris.Iris;
import android.hardware.iris.IrisManager;
import android.hardware.iris.IIrisClientActiveCallback;
import android.hardware.iris.IIrisService;
import android.hardware.iris.IIrisServiceLockoutResetCallback;
import android.hardware.iris.IIrisServiceReceiver;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IHwBinder;
import android.os.IRemoteCallback;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.KeyStore;
import android.service.iris.IrisActionStatsProto;
import android.service.iris.IrisServiceDumpProto;
import android.service.iris.IrisUserStatsProto;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.DumpUtils;
import com.android.server.SystemServerInitThreadPool;
import com.android.server.SystemService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A service to manage multiple clients that want to access the iris HAL API.
 * The service is responsible for maintaining a list of clients and dispatching all
 * iris -related events.
 *
 * @hide
 */
public class IrisService extends SystemService implements IHwBinder.DeathRecipient {
    static final String TAG = "IrisService";
    static final boolean DEBUG = true;
    private static final boolean CLEANUP_UNUSED_IRIS = false;
    private static final String IRIS_DATA_DIR = "irisdata";
    private static final int MSG_USER_SWITCHING = 10;
    private static final String ACTION_LOCKOUT_RESET =
            "com.android.server.iris.ACTION_LOCKOUT_RESET";

    private class PerformanceStats {
        int accept; // number of accepted irises
        int reject; // number of rejected irises
        int acquire; // total number of acquisitions. Should be >= accept+reject due to poor image
                     // acquisition in some cases (too fast, too slow, dirty sensor, etc.)
        int lockout; // total number of lockouts
        int permanentLockout; // total number of permanent lockouts
    }

    private final ArrayList<IrisServiceLockoutResetMonitor> mLockoutMonitors =
            new ArrayList<>();
    private final CopyOnWriteArrayList<IIrisClientActiveCallback> mClientActiveCallbacks =
            new CopyOnWriteArrayList<>();
    private final Map<Integer, Long> mAuthenticatorIds =
            Collections.synchronizedMap(new HashMap<>());
    private final AppOpsManager mAppOps;
    private static final long FAIL_LOCKOUT_TIMEOUT_MS = 30*1000;
    private static final int MAX_FAILED_ATTEMPTS_LOCKOUT_TIMED = 5;
    private static final int MAX_FAILED_ATTEMPTS_LOCKOUT_PERMANENT = 20;

    private static final long CANCEL_TIMEOUT_LIMIT = 3000; // max wait for onCancel() from HAL,in ms
    private final String mKeyguardPackage;
    private int mCurrentUserId = UserHandle.USER_NULL;
    private final IrisUtils mIrisUtils = IrisUtils.getInstance();
    private Context mContext;
    private long mHalDeviceId;
    private boolean mTimedLockoutCleared;
    private int mFailedAttempts;
    @GuardedBy("this")
    private IBiometricsIris mDaemon;
    private final PowerManager mPowerManager;
    private final AlarmManager mAlarmManager;
    private final UserManager mUserManager;
    private ClientMonitor mCurrentClient;
    private ClientMonitor mPendingClient;
    private PerformanceStats mPerformanceStats;


    private IBinder mToken = new Binder(); // used for internal IrisService enumeration
    private LinkedList<Integer> mEnumeratingUserIds = new LinkedList<>();
    private ArrayList<UserIris> mUnknownIrises = new ArrayList<>(); // hw finterprints

    private class UserIris {
        Iris f;
        int userId;
        public UserIris(Iris f, int userId) {
            this.f = f;
            this.userId = userId;
        }
    }

    // Normal iris authentications are tracked by mPerformanceMap.
    private HashMap<Integer, PerformanceStats> mPerformanceMap = new HashMap<>();

    // Transactions that make use of CryptoObjects are tracked by mCryptoPerformaceMap.
    private HashMap<Integer, PerformanceStats> mCryptoPerformanceMap = new HashMap<>();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1);
                    break;

                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    };

    private final BroadcastReceiver mLockoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_LOCKOUT_RESET.equals(intent.getAction())) {
                resetFailedAttempts(false /* clearAttemptCounter */);
            }
        }
    };

    private final Runnable mResetFailedAttemptsRunnable = new Runnable() {
        @Override
        public void run() {
            resetFailedAttempts(true /* clearAttemptCounter */);
        }
    };

    private final Runnable mResetClientState = new Runnable() {
        @Override
        public void run() {
            // Warning: if we get here, the driver never confirmed our call to cancel the current
            // operation (authenticate, enroll, remove, enumerate, etc), which is
            // really bad.  The result will be a 3-second delay in starting each new client.
            // If you see this on a device, make certain the driver notifies with
            // {@link IrisManager#IRIS_ERROR_CANCEL} in response to cancel()
            // once it has successfully switched to the IDLE state in the iris HAL.
            // Additionally,{@link IrisManager#IRIS_ERROR_CANCEL} should only be sent
            // in response to an actual cancel() call.
            Slog.w(TAG, "Client "
                    + (mCurrentClient != null ? mCurrentClient.getOwnerString() : "null")
                    + " failed to respond to cancel, starting client "
                    + (mPendingClient != null ? mPendingClient.getOwnerString() : "null"));

            mCurrentClient = null;
            startClient(mPendingClient, false);
        }
    };

    public IrisService(Context context) {
        super(context);
        mContext = context;
        mKeyguardPackage = ComponentName.unflattenFromString(context.getResources().getString(
                com.android.internal.R.string.config_keyguardComponent)).getPackageName();
        mAppOps = context.getSystemService(AppOpsManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mContext.registerReceiver(mLockoutReceiver, new IntentFilter(ACTION_LOCKOUT_RESET),
                RESET_IRIS_LOCKOUT, null /* handler */);
        mUserManager = UserManager.get(mContext);
    }

    @Override
    public void serviceDied(long cookie) {
        Slog.v(TAG, "iris HAL died");
        MetricsLogger.count(mContext, "irisd_died", 1);
        handleError(mHalDeviceId, IrisManager.IRIS_ERROR_HW_UNAVAILABLE,
                0 /*vendorCode */);
    }

    public synchronized IBiometricsIris getIrisDaemon() {
        if (mDaemon == null) {
            Slog.v(TAG, "mDeamon was null, reconnect to iris");
            try {
                mDaemon = IBiometricsIris.getService();
            } catch (java.util.NoSuchElementException e) {
                // Service doesn't exist or cannot be opened. Logged below.
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get biometric interface", e);
            }
            if (mDaemon == null) {
                Slog.w(TAG, "iris HIDL not available");
                return null;
            }

            mDaemon.asBinder().linkToDeath(this, 0);

            try {
                mHalDeviceId = mDaemon.setNotify(mDaemonCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to open iris HAL", e);
                mDaemon = null; // try again later!
            }

            if (DEBUG) Slog.v(TAG, "Iris HAL id: " + mHalDeviceId);
            if (mHalDeviceId != 0) {
                loadAuthenticatorIds();
                updateActiveGroup(ActivityManager.getCurrentUser(), null);
                doIrisCleanup(ActivityManager.getCurrentUser());
            } else {
                Slog.w(TAG, "Failed to open Iris HAL!");
                MetricsLogger.count(mContext, "irisd_openhal_error", 1);
                mDaemon = null;
            }
        }
        return mDaemon;
    }

    /** Populates existing authenticator ids. To be used only during the start of the service. */
    private void loadAuthenticatorIds() {
        // This operation can be expensive, so keep track of the elapsed time. Might need to move to
        // background if it takes too long.
        long t = System.currentTimeMillis();
        mAuthenticatorIds.clear();
        for (UserInfo user : UserManager.get(mContext).getUsers(true /* excludeDying */)) {
            int userId = getUserOrWorkProfileId(null, user.id);
            if (!mAuthenticatorIds.containsKey(userId)) {
                updateActiveGroup(userId, null);
            }
        }

        t = System.currentTimeMillis() - t;
        if (t > 1000) {
            Slog.w(TAG, "loadAuthenticatorIds() taking too long: " + t + "ms");
        }
    }

    private void doIrisCleanup(int userId) {
        if (CLEANUP_UNUSED_IRIS) {
            resetEnumerateState();
            mEnumeratingUserIds.push(userId);
            enumerateNextUser();
        }
    }

    private void resetEnumerateState() {
        if (DEBUG) Slog.v(TAG, "Enumerate cleaning up");
        mEnumeratingUserIds.clear();
        mUnknownIrises.clear();
    }

    private void enumerateNextUser() {
        int nextUser = mEnumeratingUserIds.getFirst();
        updateActiveGroup(nextUser, null);
        boolean restricted = !hasPermission(MANAGE_IRIS);

        if (DEBUG) Slog.v(TAG, "Enumerating user id " + nextUser + " of "
                + mEnumeratingUserIds.size() + " remaining users");

        startEnumerate(mToken, nextUser, null, restricted, true /* internal */);
    }

    // Remove unknown irises from hardware
    private void cleanupUnknownIrises() {
        if (!mUnknownIrises.isEmpty()) {
            Slog.w(TAG, "unknown iris size: " + mUnknownIrises.size());
            UserIris uf = mUnknownIrises.get(0);
            mUnknownIrises.remove(uf);
            boolean restricted = !hasPermission(MANAGE_IRIS);
            updateActiveGroup(uf.userId, null);
            startRemove(mToken, uf.f.getIrisId(), uf.f.getGroupId(), uf.userId, null,
                    restricted, true /* internal */);
        } else {
            resetEnumerateState();
        }
    }

    protected void handleEnumerate(long deviceId, int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "Enumerate: id=" + irisId
                + ", gid=" + groupId
                + ", dev=" + deviceId
                + ", rem=" + remaining);

        ClientMonitor client = mCurrentClient;

        if ( !(client instanceof InternalRemovalClient) && !(client instanceof EnumerateClient) ) {
            return;
        }
        client.onEnumerationResult(irisId, groupId, remaining);

        // All irises in hardware for this user were enumerated
        if (remaining == 0) {
            mEnumeratingUserIds.poll();

            if (client instanceof InternalEnumerateClient) {
                List<Iris> enrolled = ((InternalEnumerateClient) client).getEnumeratedList();
                Slog.w(TAG, "Added " + enrolled.size() + " enumerated irises for deletion");
                for (Iris f : enrolled) {
                    mUnknownIrises.add(new UserIris(f, client.getTargetUserId()));
                }
            }

            removeClient(client);

            if (!mEnumeratingUserIds.isEmpty()) {
                enumerateNextUser();
            } else if (client instanceof InternalEnumerateClient) {
                if (DEBUG) Slog.v(TAG, "Finished enumerating all users");
                // This will start a chain of InternalRemovalClients
                cleanupUnknownIrises();
            }
        }
    }

    protected void handleError(long deviceId, int error, int vendorCode) {
        ClientMonitor client = mCurrentClient;
        if (client instanceof InternalRemovalClient || client instanceof InternalEnumerateClient) {
            resetEnumerateState();
        }
        if (client != null && client.onError(error, vendorCode)) {
            removeClient(client);
        }

        if (DEBUG) Slog.v(TAG, "handleError(client="
                + (client != null ? client.getOwnerString() : "null") + ", error = " + error + ")");
        // This is the magic code that starts the next client when the old client finishes.
        if (error == IrisManager.IRIS_ERROR_CANCELED) {
            mHandler.removeCallbacks(mResetClientState);
            if (mPendingClient != null) {
                if (DEBUG) Slog.v(TAG, "start pending client " + mPendingClient.getOwnerString());
                startClient(mPendingClient, false);
                mPendingClient = null;
            }
        } else if (error == IrisManager.IRIS_ERROR_HW_UNAVAILABLE) {
            // If we get HW_UNAVAILABLE, try to connect again later...
            Slog.w(TAG, "Got ERROR_HW_UNAVAILABLE; try reconnecting next client.");
            synchronized (this) {
                mDaemon = null;
                mHalDeviceId = 0;
                mCurrentUserId = UserHandle.USER_NULL;
            }
        }
    }

    protected void handleRemoved(long deviceId, int irisId, int groupId, int remaining) {
        if (DEBUG) Slog.w(TAG, "Removed: id=" + irisId
                + ", gid=" + groupId
                + ", dev=" + deviceId
                + ", rem=" + remaining);

        ClientMonitor client = mCurrentClient;
        if (client != null && client.onRemoved(irisId, groupId, remaining)) {
            removeClient(client);
            // When the last iris of a group is removed, update the authenticator id
            if (!hasEnrolledIrises(groupId)) {
                updateActiveGroup(groupId, null);
            }
        }
        if (client instanceof InternalRemovalClient && !mUnknownIrises.isEmpty()) {
            cleanupUnknownIrises();
        } else if (client instanceof InternalRemovalClient){
            resetEnumerateState();
        }
    }

    protected void handleAuthenticated(long deviceId, int irisId, int groupId,
            ArrayList<Byte> token) {
        ClientMonitor client = mCurrentClient;
        if (irisId != 0) {
            // Ugh...
            final byte[] byteToken = new byte[token.size()];
            for (int i = 0; i < token.size(); i++) {
                byteToken[i] = token.get(i);
            }
            // Send to Keystore
            KeyStore.getInstance().addAuthToken(byteToken);
        }
        if (client != null && client.onAuthenticated(irisId, groupId)) {
            removeClient(client);
        }
        if (irisId != 0) {
            mPerformanceStats.accept++;
        } else {
            mPerformanceStats.reject++;
        }
    }

    protected void handleAcquired(long deviceId, int acquiredInfo, int vendorCode) {
        ClientMonitor client = mCurrentClient;
        if (client != null && client.onAcquired(acquiredInfo, vendorCode)) {
            removeClient(client);
        }
        if (mPerformanceStats != null && getLockoutMode() == AuthenticationClient.LOCKOUT_NONE
                && client instanceof AuthenticationClient) {
            // ignore enrollment acquisitions or acquisitions when we're locked out
            mPerformanceStats.acquire++;
        }
    }

    protected void handleEnrollResult(long deviceId, int irisId, int groupId, int remaining) {
        ClientMonitor client = mCurrentClient;
        if (client != null && client.onEnrollResult(irisId, groupId, remaining)) {
            removeClient(client);
            // When enrollment finishes, update this group's authenticator id, as the HAL has
            // already generated a new authenticator id when the new iris is enrolled.
            updateActiveGroup(groupId, null);
        }
    }

    private void userActivity() {
        long now = SystemClock.uptimeMillis();
        mPowerManager.userActivity(now, PowerManager.USER_ACTIVITY_EVENT_TOUCH, 0);
    }

    void handleUserSwitching(int userId) {
        updateActiveGroup(userId, null);
        doIrisCleanup(userId);
    }

    private void removeClient(ClientMonitor client) {
        if (client != null) {
            client.destroy();
            if (client != mCurrentClient && mCurrentClient != null) {
                Slog.w(TAG, "Unexpected client: " + client.getOwnerString() + "expected: "
                        + mCurrentClient != null ? mCurrentClient.getOwnerString() : "null");
            }
        }
        if (mCurrentClient != null) {
            if (DEBUG) Slog.v(TAG, "Done with client: " + client.getOwnerString());
            mCurrentClient = null;
        }
        if (mPendingClient == null) {
            notifyClientActiveCallbacks(false);
        }
    }

    private int getLockoutMode() {
        if (mFailedAttempts >= MAX_FAILED_ATTEMPTS_LOCKOUT_PERMANENT) {
            return AuthenticationClient.LOCKOUT_PERMANENT;
        } else if (mFailedAttempts > 0 && mTimedLockoutCleared == false &&
                (mFailedAttempts % MAX_FAILED_ATTEMPTS_LOCKOUT_TIMED == 0)) {
            return AuthenticationClient.LOCKOUT_TIMED;
        }
        return AuthenticationClient.LOCKOUT_NONE;
    }

    private void scheduleLockoutReset() {
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + FAIL_LOCKOUT_TIMEOUT_MS, getLockoutResetIntent());
    }

    private void cancelLockoutReset() {
        mAlarmManager.cancel(getLockoutResetIntent());
    }

    private PendingIntent getLockoutResetIntent() {
        return PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_LOCKOUT_RESET), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public long startPreEnroll(IBinder token) {
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPreEnroll: no iris HAL!");
            return 0;
        }
        try {
            return daemon.preEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPreEnroll failed", e);
        }
        return 0;
    }

    public int startPostEnroll(IBinder token) {
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPostEnroll: no iris HAL!");
            return 0;
        }
        try {
            return daemon.postEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPostEnroll failed", e);
        }
        return 0;
    }

    /**
     * Calls iris HAL to switch states to the new task. If there's already a current task,
     * it calls cancel() and sets mPendingClient to begin when the current task finishes
     * ({@link IrisManager#IRIS_ERROR_CANCELED}).
     * @param newClient the new client that wants to connect
     * @param initiatedByClient true for authenticate, remove and enroll
     */
    private void startClient(ClientMonitor newClient, boolean initiatedByClient) {
        ClientMonitor currentClient = mCurrentClient;
        if (currentClient != null) {
            if (DEBUG) Slog.v(TAG, "request stop current client " + currentClient.getOwnerString());
            if (currentClient instanceof InternalEnumerateClient ||
                    currentClient instanceof InternalRemovalClient) {
                // This condition means we're currently running internal diagnostics to
                // remove extra irises in the hardware and/or the software
                // TODO: design an escape hatch in case client never finishes
            }
            else {
                currentClient.stop(initiatedByClient);
            }
            mPendingClient = newClient;
            mHandler.removeCallbacks(mResetClientState);
            mHandler.postDelayed(mResetClientState, CANCEL_TIMEOUT_LIMIT);
        } else if (newClient != null) {
            mCurrentClient = newClient;
            if (DEBUG) Slog.v(TAG, "starting client "
                    + newClient.getClass().getSuperclass().getSimpleName()
                    + "(" + newClient.getOwnerString() + ")"
                    + ", initiatedByClient = " + initiatedByClient + ")");
            notifyClientActiveCallbacks(true);

            newClient.start();
        }
    }

    void startRemove(IBinder token, int irisId, int groupId, int userId,
            IIrisServiceReceiver receiver, boolean restricted, boolean internal) {
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startRemove: no iris HAL!");
            return;
        }

        if (internal) {
            Context context = getContext();
            InternalRemovalClient client = new InternalRemovalClient(context, mHalDeviceId,
                    token, receiver, irisId, groupId, userId, restricted,
                    context.getOpPackageName()) {
                @Override
                public void notifyUserActivity() {

                }
                @Override
                public IBiometricsIris getIrisDaemon() {
                    return IrisService.this.getIrisDaemon();
                }
            };
            startClient(client, true);
        }
        else {
            RemovalClient client = new RemovalClient(getContext(), mHalDeviceId, token,
                    receiver, irisId, groupId, userId, restricted, token.toString()) {
                @Override
                public void notifyUserActivity() {
                    IrisService.this.userActivity();
                }

                @Override
                public IBiometricsIris getIrisDaemon() {
                    return IrisService.this.getIrisDaemon();
                }
            };
            startClient(client, true);
        }
    }

    void startEnumerate(IBinder token, int userId,
        IIrisServiceReceiver receiver, boolean restricted, boolean internal) {
        IBiometricsIris daemon = getIrisDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startEnumerate: no iris HAL!");
            return;
        }
        if (internal) {
            List<Iris> enrolledList = getEnrolledIrises(userId);
            Context context = getContext();
            InternalEnumerateClient client = new InternalEnumerateClient(context, mHalDeviceId,
                    token, receiver, userId, userId, restricted, context.getOpPackageName(),
                    enrolledList) {
                @Override
                public void notifyUserActivity() {

                }

                @Override
                public IBiometricsIris getIrisDaemon() {
                    return IrisService.this.getIrisDaemon();
                }
            };
            startClient(client, true);
        }
        else {
            EnumerateClient client = new EnumerateClient(getContext(), mHalDeviceId, token,
                    receiver, userId, userId, restricted, token.toString()) {
                @Override
                public void notifyUserActivity() {
                    IrisService.this.userActivity();
                }

                @Override
                public IBiometricsIris getIrisDaemon() {
                    return IrisService.this.getIrisDaemon();
                }
            };
            startClient(client, true);
        }
    }

    public List<Iris> getEnrolledIrises(int userId) {
        return mIrisUtils.getIrisesForUser(mContext, userId);
    }

    public boolean hasEnrolledIrises(int userId) {
        if (userId != UserHandle.getCallingUserId()) {
            checkPermission(INTERACT_ACROSS_USERS);
        }
        return mIrisUtils.getIrisesForUser(mContext, userId).size() > 0;
    }

    boolean hasPermission(String permission) {
        return getContext().checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    void checkPermission(String permission) {
        getContext().enforceCallingOrSelfPermission(permission,
                "Must have " + permission + " permission.");
    }

    int getEffectiveUserId(int userId) {
        UserManager um = UserManager.get(mContext);
        if (um != null) {
            final long callingIdentity = Binder.clearCallingIdentity();
            userId = um.getCredentialOwnerProfile(userId);
            Binder.restoreCallingIdentity(callingIdentity);
        } else {
            Slog.e(TAG, "Unable to acquire UserManager");
        }
        return userId;
    }

    boolean isCurrentUserOrProfile(int userId) {
        UserManager um = UserManager.get(mContext);
        if (um == null) {
            Slog.e(TAG, "Unable to acquire UserManager");
            return false;
        }

        final long token = Binder.clearCallingIdentity();
        try {
            // Allow current user or profiles of the current user...
            for (int profileId : um.getEnabledProfileIds(ActivityManager.getCurrentUser())) {
                if (profileId == userId) {
                    return true;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        return false;
    }

    private boolean isForegroundActivity(int uid, int pid) {
        try {
            List<RunningAppProcessInfo> procs =
                    ActivityManager.getService().getRunningAppProcesses();
            int N = procs.size();
            for (int i = 0; i < N; i++) {
                RunningAppProcessInfo proc = procs.get(i);
                if (proc.pid == pid && proc.uid == uid
                        && proc.importance == IMPORTANCE_FOREGROUND) {
                    return true;
                }
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "am.getRunningAppProcesses() failed");
        }
        return false;
    }

    /**
     * @param opPackageName name of package for caller
     * @param requireForeground only allow this call while app is in the foreground
     * @return true if caller can use iris API
     */
    private boolean canUseIris(String opPackageName, boolean requireForeground, int uid,
            int pid, int userId) {
        checkPermission(USE_IRIS);
        if (isKeyguard(opPackageName)) {
            return true; // Keyguard is always allowed
        }
        if (!isCurrentUserOrProfile(userId)) {
            Slog.w(TAG,"Rejecting " + opPackageName + " ; not a current user or profile");
            return false;
        }
        if (mAppOps.noteOp(AppOpsManager.OP_USE_FINGERPRINT, uid, opPackageName)
                != AppOpsManager.MODE_ALLOWED) {
            Slog.w(TAG, "Rejecting " + opPackageName + " ; permission denied");
            return false;
        }
        if (requireForeground && !(isForegroundActivity(uid, pid) || currentClient(opPackageName))){
            Slog.w(TAG, "Rejecting " + opPackageName + " ; not in foreground");
            return false;
        }
        return true;
    }

    /**
     * @param opPackageName package of the caller
     * @return true if this is the same client currently using iris
     */
    private boolean currentClient(String opPackageName) {
        return mCurrentClient != null && mCurrentClient.getOwnerString().equals(opPackageName);
    }

    /**
     * @param clientPackage
     * @return true if this is keyguard package
     */
    private boolean isKeyguard(String clientPackage) {
        return mKeyguardPackage.equals(clientPackage);
    }

    private void addLockoutResetMonitor(IrisServiceLockoutResetMonitor monitor) {
        if (!mLockoutMonitors.contains(monitor)) {
            mLockoutMonitors.add(monitor);
        }
    }

    private void removeLockoutResetCallback(
            IrisServiceLockoutResetMonitor monitor) {
        mLockoutMonitors.remove(monitor);
    }

    private void notifyLockoutResetMonitors() {
        for (int i = 0; i < mLockoutMonitors.size(); i++) {
            mLockoutMonitors.get(i).sendLockoutReset();
        }
    }

    private void notifyClientActiveCallbacks(boolean isActive) {
        List<IIrisClientActiveCallback> callbacks = mClientActiveCallbacks;
        for (int i = 0; i < callbacks.size(); i++) {
            try {
                callbacks.get(i).onClientActiveChanged(isActive);
            } catch (RemoteException re) {
                // If the remote is dead, stop notifying it
                mClientActiveCallbacks.remove(callbacks.get(i));
            }
        }
    }

    private void startAuthentication(IBinder token, long opId, int callingUserId, int groupId,
                IIrisServiceReceiver receiver, int flags, boolean restricted,
                String opPackageName) {
        updateActiveGroup(groupId, opPackageName);

        if (DEBUG) Slog.v(TAG, "startAuthentication(" + opPackageName + ")");

        AuthenticationClient client = new AuthenticationClient(getContext(), mHalDeviceId, token,
                receiver, mCurrentUserId, groupId, opId, restricted, opPackageName) {
            @Override
            public int handleFailedAttempt() {
                mFailedAttempts++;
                mTimedLockoutCleared = false;
                final int lockoutMode = getLockoutMode();
                if (lockoutMode == AuthenticationClient.LOCKOUT_PERMANENT) {
                    mPerformanceStats.permanentLockout++;
                } else if (lockoutMode == AuthenticationClient.LOCKOUT_TIMED) {
                    mPerformanceStats.lockout++;
                }

                // Failing multiple times will continue to push out the lockout time
                if (lockoutMode != AuthenticationClient.LOCKOUT_NONE) {
                    scheduleLockoutReset();
                    return lockoutMode;
                }
                return AuthenticationClient.LOCKOUT_NONE;
            }

            @Override
            public void resetFailedAttempts() {
                IrisService.this.resetFailedAttempts(true /* clearAttemptCounter */);
            }

            @Override
            public void notifyUserActivity() {
                IrisService.this.userActivity();
            }

            @Override
            public IBiometricsIris getIrisDaemon() {
                return IrisService.this.getIrisDaemon();
            }
        };

        int lockoutMode = getLockoutMode();
        if (lockoutMode != AuthenticationClient.LOCKOUT_NONE) {
            Slog.v(TAG, "In lockout mode(" + lockoutMode +
                    ") ; disallowing authentication");
            int errorCode = lockoutMode == AuthenticationClient.LOCKOUT_TIMED ?
                    IrisManager.IRIS_ERROR_LOCKOUT :
                    IrisManager.IRIS_ERROR_LOCKOUT_PERMANENT;
            if (!client.onError(errorCode, 0 /* vendorCode */)) {
                Slog.w(TAG, "Cannot send permanent lockout message to client");
            }
            return;
        }
        startClient(client, true /* initiatedByClient */);
    }

    private void startEnrollment(IBinder token, byte [] cryptoToken, int userId,
            IIrisServiceReceiver receiver, int flags, boolean restricted,
            String opPackageName) {
        updateActiveGroup(userId, opPackageName);

        final int groupId = userId; // default group for iris enrollment

        EnrollClient client = new EnrollClient(getContext(), mHalDeviceId, token, receiver,
                userId, groupId, cryptoToken, restricted, opPackageName) {

            @Override
            public IBiometricsIris getIrisDaemon() {
                return IrisService.this.getIrisDaemon();
            }

            @Override
            public void notifyUserActivity() {
                IrisService.this.userActivity();
            }
        };
        startClient(client, true /* initiatedByClient */);
    }

    // attempt counter should only be cleared when Keyguard goes away or when
    // a iris is successfully authenticated
    protected void resetFailedAttempts(boolean clearAttemptCounter) {
        if (DEBUG && getLockoutMode() != AuthenticationClient.LOCKOUT_NONE) {
            Slog.v(TAG, "Reset iris lockout, clearAttemptCounter=" + clearAttemptCounter);
        }
        if (clearAttemptCounter) {
            mFailedAttempts = 0;
        }
        mTimedLockoutCleared = true;
        // If we're asked to reset failed attempts externally (i.e. from Keyguard),
        // the alarm might still be pending; remove it.
        cancelLockoutReset();
        notifyLockoutResetMonitors();
    }

    private class IrisServiceLockoutResetMonitor {

        private static final long WAKELOCK_TIMEOUT_MS = 2000;
        private final IIrisServiceLockoutResetCallback mCallback;
        private final WakeLock mWakeLock;

        public IrisServiceLockoutResetMonitor(
                IIrisServiceLockoutResetCallback callback) {
            mCallback = callback;
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "lockout reset callback");
        }

        public void sendLockoutReset() {
            if (mCallback != null) {
                try {
                    mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                    mCallback.onLockoutReset(mHalDeviceId, new IRemoteCallback.Stub() {

                        @Override
                        public void sendResult(Bundle data) throws RemoteException {
                            if (mWakeLock.isHeld()) {
                                mWakeLock.release();
                            }
                        }
                    });
                } catch (DeadObjectException e) {
                    Slog.w(TAG, "Death object while invoking onLockoutReset: ", e);
                    mHandler.post(mRemoveCallbackRunnable);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke onLockoutReset: ", e);
                }
            }
        }

        private final Runnable mRemoveCallbackRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                removeLockoutResetCallback(IrisServiceLockoutResetMonitor.this);
            }
        };
    }

    private IBiometricsIrisClientCallback mDaemonCallback =
            new IBiometricsIrisClientCallback.Stub() {

        @Override
        public void onEnrollResult(final long deviceId, final int irisId, final int groupId,
                final int remaining) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleEnrollResult(deviceId, irisId, groupId, remaining);
                }
            });
        }

        @Override
        public void onAcquired(final long deviceId, final int acquiredInfo, final int vendorCode) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleAcquired(deviceId, acquiredInfo, vendorCode);
                }
            });
        }

        @Override
        public void onAuthenticated(final long deviceId, final int irisId, final int groupId,
                ArrayList<Byte> token) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleAuthenticated(deviceId, irisId, groupId, token);
                }
            });
        }

        @Override
        public void onError(final long deviceId, final int error, final int vendorCode) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleError(deviceId, error, vendorCode);
                }
            });
        }

        @Override
        public void onRemoved(final long deviceId, final int irisId, final int groupId, final int remaining) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleRemoved(deviceId, irisId, groupId, remaining);
                }
            });
        }

        @Override
        public void onEnumerate(final long deviceId, final int irisId, final int groupId,
                final int remaining) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleEnumerate(deviceId, irisId, groupId, remaining);
                }
            });
        }
    };

    private final class IrisServiceWrapper extends IIrisService.Stub {
        @Override // Binder call
        public long preEnroll(IBinder token) {
            checkPermission(MANAGE_IRIS);
            return startPreEnroll(token);
        }

        @Override // Binder call
        public int postEnroll(IBinder token) {
            checkPermission(MANAGE_IRIS);
            return startPostEnroll(token);
        }

        @Override // Binder call
        public void enroll(final IBinder token, final byte[] cryptoToken, final int userId,
                final IIrisServiceReceiver receiver, final int flags,
                final String opPackageName) {
            checkPermission(MANAGE_IRIS);
            final int limit =  mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_irisMaxTemplatesPerUser);

            final int enrolled = IrisService.this.getEnrolledIrises(userId).size();
            if (enrolled >= limit) {
                Slog.w(TAG, "Too many irises registered");
                return;
            }

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default irises for the user.
            if (!isCurrentUserOrProfile(userId)) {
                return;
            }

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startEnrollment(token, cryptoToken, userId, receiver, flags,
                            restricted, opPackageName);
                }
            });
        }

        private boolean isRestricted() {
            // Only give privileged apps (like Settings) access to iris info
            final boolean restricted = !hasPermission(MANAGE_IRIS);
            return restricted;
        }

        @Override // Binder call
        public void cancelEnrollment(final IBinder token) {
            checkPermission(MANAGE_IRIS);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ClientMonitor client = mCurrentClient;
                    if (client instanceof EnrollClient && client.getToken() == token) {
                        client.stop(client.getToken() == token);
                    }
                }
            });
        }

        @Override // Binder call
        public void authenticate(final IBinder token, final long opId, final int groupId,
                final IIrisServiceReceiver receiver, final int flags,
                final String opPackageName) {
            final int callingUid = Binder.getCallingUid();
            final int callingPid = Binder.getCallingPid();
            final int callingUserId = UserHandle.getCallingUserId();
            final boolean restricted = isRestricted();

            if (!canUseIris(opPackageName, true /* foregroundOnly */, callingUid, callingPid,
                    callingUserId)) {
                if (DEBUG) Slog.v(TAG, "authenticate(): reject " + opPackageName);
                return;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    MetricsLogger.histogram(mContext, "iris_token", opId != 0L ? 1 : 0);

                    // Get performance stats object for this user.
                    HashMap<Integer, PerformanceStats> pmap
                            = (opId == 0) ? mPerformanceMap : mCryptoPerformanceMap;
                    PerformanceStats stats = pmap.get(mCurrentUserId);
                    if (stats == null) {
                        stats = new PerformanceStats();
                        pmap.put(mCurrentUserId, stats);
                    }
                    mPerformanceStats = stats;

                    startAuthentication(token, opId, callingUserId, groupId, receiver,
                            flags, restricted, opPackageName);
                }
            });
        }

        @Override // Binder call
        public void cancelAuthentication(final IBinder token, final String opPackageName) {
            final int callingUid = Binder.getCallingUid();
            final int callingPid = Binder.getCallingPid();
            final int callingUserId = UserHandle.getCallingUserId();

            if (!canUseIris(opPackageName, true /* foregroundOnly */, callingUid, callingPid,
                    callingUserId)) {
                if (DEBUG) Slog.v(TAG, "cancelAuthentication(): reject " + opPackageName);
                return;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ClientMonitor client = mCurrentClient;
                    if (client instanceof AuthenticationClient) {
                        if (client.getToken() == token) {
                            if (DEBUG) Slog.v(TAG, "stop client " + client.getOwnerString());
                            client.stop(client.getToken() == token);
                        } else {
                            if (DEBUG) Slog.v(TAG, "can't stop client "
                                    + client.getOwnerString() + " since tokens don't match");
                        }
                    } else if (client != null) {
                        if (DEBUG) Slog.v(TAG, "can't cancel non-authenticating client "
                                + client.getOwnerString());
                    }
                }
            });
        }

        @Override // Binder call
        public void setActiveUser(final int userId) {
            checkPermission(MANAGE_IRIS);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateActiveGroup(userId, null);
                }
            });
        }

        @Override // Binder call
        public void remove(final IBinder token, final int irisId, final int groupId,
                final int userId, final IIrisServiceReceiver receiver) {
            checkPermission(MANAGE_IRIS); // TODO: Maybe have another permission
            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startRemove(token, irisId, groupId, userId, receiver,
                            restricted, false /* internal */);
                }
            });
        }

        @Override // Binder call
        public void enumerate(final IBinder token, final int userId,
            final IIrisServiceReceiver receiver) {
            checkPermission(MANAGE_IRIS); // TODO: Maybe have another permission
            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startEnumerate(token, userId, receiver, restricted, false /* internal */);
                }
            });
        }

        @Override // Binder call
        public boolean isHardwareDetected(long deviceId, String opPackageName) {
            if (!canUseIris(opPackageName, false /* foregroundOnly */,
                    Binder.getCallingUid(), Binder.getCallingPid(),
                    UserHandle.getCallingUserId())) {
                return false;
            }

            final long token = Binder.clearCallingIdentity();
            try {
                IBiometricsIris daemon = getIrisDaemon();
                return daemon != null && mHalDeviceId != 0;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override // Binder call
        public void rename(final int irisId, final int groupId, final String name) {
            checkPermission(MANAGE_IRIS);
            if (!isCurrentUserOrProfile(groupId)) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mIrisUtils.renameIrisForUser(mContext, irisId,
                            groupId, name);
                }
            });
        }

        @Override // Binder call
        public List<Iris> getEnrolledIrises(int userId, String opPackageName) {
            if (!canUseIris(opPackageName, false /* foregroundOnly */,
                    Binder.getCallingUid(), Binder.getCallingPid(),
                    UserHandle.getCallingUserId())) {
                return Collections.emptyList();
            }

            return IrisService.this.getEnrolledIrises(userId);
        }

        @Override // Binder call
        public boolean hasEnrolledIrises(int userId, String opPackageName) {
            if (!canUseIris(opPackageName, false /* foregroundOnly */,
                    Binder.getCallingUid(), Binder.getCallingPid(),
                    UserHandle.getCallingUserId())) {
                return false;
            }

            return IrisService.this.hasEnrolledIrises(userId);
        }

        @Override // Binder call
        public long getAuthenticatorId(String opPackageName) {
            // In this method, we're not checking whether the caller is permitted to use iris
            // API because current authenticator ID is leaked (in a more contrived way) via Android
            // Keystore (android.security.keystore package): the user of that API can create a key
            // which requires iris authentication for its use, and then query the key's
            // characteristics (hidden API) which returns, among other things, iris
            // authenticator ID which was active at key creation time.
            //
            // Reason: The part of Android Keystore which runs inside an app's process invokes this
            // method in certain cases. Those cases are not always where the developer demonstrates
            // explicit intent to use iris functionality. Thus, to avoiding throwing an
            // unexpected SecurityException this method does not check whether its caller is
            // permitted to use iris API.
            //
            // The permission check should be restored once Android Keystore no longer invokes this
            // method from inside app processes.

            return IrisService.this.getAuthenticatorId(opPackageName);
        }

        @Override // Binder call
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;

            final long ident = Binder.clearCallingIdentity();
            try {
                if (args.length > 0 && "--proto".equals(args[0])) {
                    dumpProto(fd);
                } else {
                    dumpInternal(pw);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void resetTimeout(byte [] token) {
            checkPermission(RESET_IRIS_LOCKOUT);
            // TODO: confirm security token when we move timeout management into the HAL layer.
            mHandler.post(mResetFailedAttemptsRunnable);
        }

        @Override
        public void addLockoutResetCallback(final IIrisServiceLockoutResetCallback callback)
                throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    addLockoutResetMonitor(
                            new IrisServiceLockoutResetMonitor(callback));
                }
            });
        }

        @Override
        public boolean isClientActive() {
            checkPermission(MANAGE_IRIS);
            synchronized(IrisService.this) {
                return (mCurrentClient != null) || (mPendingClient != null);
            }
        }

        @Override
        public void addClientActiveCallback(IIrisClientActiveCallback callback) {
            checkPermission(MANAGE_IRIS);
            mClientActiveCallbacks.add(callback);
        }

        @Override
        public void removeClientActiveCallback(IIrisClientActiveCallback callback) {
            checkPermission(MANAGE_IRIS);
            mClientActiveCallbacks.remove(callback);
        }
    }

    private void dumpInternal(PrintWriter pw) {
        JSONObject dump = new JSONObject();
        try {
            dump.put("service", "Iris Manager");

            JSONArray sets = new JSONArray();
            for (UserInfo user : UserManager.get(getContext()).getUsers()) {
                final int userId = user.getUserHandle().getIdentifier();
                final int N = mIrisUtils.getIrisesForUser(mContext, userId).size();
                PerformanceStats stats = mPerformanceMap.get(userId);
                PerformanceStats cryptoStats = mCryptoPerformanceMap.get(userId);
                JSONObject set = new JSONObject();
                set.put("id", userId);
                set.put("count", N);
                set.put("accept", (stats != null) ? stats.accept : 0);
                set.put("reject", (stats != null) ? stats.reject : 0);
                set.put("acquire", (stats != null) ? stats.acquire : 0);
                set.put("lockout", (stats != null) ? stats.lockout : 0);
                set.put("permanentLockout", (stats != null) ? stats.permanentLockout : 0);
                // cryptoStats measures statistics about secure iris transactions
                // (e.g. to unlock password storage, make secure purchases, etc.)
                set.put("acceptCrypto", (cryptoStats != null) ? cryptoStats.accept : 0);
                set.put("rejectCrypto", (cryptoStats != null) ? cryptoStats.reject : 0);
                set.put("acquireCrypto", (cryptoStats != null) ? cryptoStats.acquire : 0);
                set.put("lockoutCrypto", (cryptoStats != null) ? cryptoStats.lockout : 0);
                sets.put(set);
            }

            dump.put("prints", sets);
        } catch (JSONException e) {
            Slog.e(TAG, "dump formatting failure", e);
        }
        pw.println(dump);
    }

    private void dumpProto(FileDescriptor fd) {
        final ProtoOutputStream proto = new ProtoOutputStream(fd);
        for (UserInfo user : UserManager.get(getContext()).getUsers()) {
            final int userId = user.getUserHandle().getIdentifier();

            final long userToken = proto.start(IrisServiceDumpProto.USERS);

            proto.write(IrisUserStatsProto.USER_ID, userId);
            proto.write(IrisUserStatsProto.NUM_IRISES,
                    mIrisUtils.getIrisesForUser(mContext, userId).size());

            // Normal iris authentications (e.g. lockscreen)
            final PerformanceStats normal = mPerformanceMap.get(userId);
            if (normal != null) {
                final long countsToken = proto.start(IrisUserStatsProto.NORMAL);
                proto.write(IrisActionStatsProto.ACCEPT, normal.accept);
                proto.write(IrisActionStatsProto.REJECT, normal.reject);
                proto.write(IrisActionStatsProto.ACQUIRE, normal.acquire);
                proto.write(IrisActionStatsProto.LOCKOUT, normal.lockout);
                proto.write(IrisActionStatsProto.LOCKOUT_PERMANENT, normal.lockout);
                proto.end(countsToken);
            }

            // Statistics about secure iris transactions (e.g. to unlock password
            // storage, make secure purchases, etc.)
            final PerformanceStats crypto = mCryptoPerformanceMap.get(userId);
            if (crypto != null) {
                final long countsToken = proto.start(IrisUserStatsProto.CRYPTO);
                proto.write(IrisActionStatsProto.ACCEPT, crypto.accept);
                proto.write(IrisActionStatsProto.REJECT, crypto.reject);
                proto.write(IrisActionStatsProto.ACQUIRE, crypto.acquire);
                proto.write(IrisActionStatsProto.LOCKOUT, crypto.lockout);
                proto.write(IrisActionStatsProto.LOCKOUT_PERMANENT, crypto.lockout);
                proto.end(countsToken);
            }

            proto.end(userToken);
        }
        proto.flush();
    }

    @Override
    public void onStart() {
        publishBinderService(Context.IRIS_SERVICE, new IrisServiceWrapper());
        SystemServerInitThreadPool.get().submit(this::getIrisDaemon, TAG + ".onStart");
        listenForUserSwitches();
    }

    private void updateActiveGroup(int userId, String clientPackage) {
        IBiometricsIris daemon = getIrisDaemon();

        if (daemon != null) {
            try {
                userId = getUserOrWorkProfileId(clientPackage, userId);
                if (userId != mCurrentUserId) {
                    final File systemDir = Environment.getUserSystemDirectory(userId);
                    final File irisDir = new File(systemDir, IRIS_DATA_DIR);
                    if (!irisDir.exists()) {
                        if (!irisDir.mkdir()) {
                            Slog.v(TAG, "Cannot make directory: " + irisDir.getAbsolutePath());
                            return;
                        }
                        // Calling mkdir() from this process will create a directory with our
                        // permissions (inherited from the containing dir). This command fixes
                        // the label.
                        if (!SELinux.restorecon(irisDir)) {
                            Slog.w(TAG, "Restorecons failed. Directory will have wrong label.");
                            return;
                        }
                    }
                    daemon.setActiveGroup(userId, irisDir.getAbsolutePath());
                    mCurrentUserId = userId;
                }
                mAuthenticatorIds.put(userId,
                        hasEnrolledIrises(userId) ? daemon.getAuthenticatorId() : 0L);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to setActiveGroup():", e);
            }
        }
    }

    /**
     * @param clientPackage the package of the caller
     * @return the profile id
     */
    private int getUserOrWorkProfileId(String clientPackage, int userId) {
        if (!isKeyguard(clientPackage) && isWorkProfile(userId)) {
            return userId;
        }
        return getEffectiveUserId(userId);
    }

    /**
     * @param userId
     * @return true if this is a work profile
     */
    private boolean isWorkProfile(int userId) {
        UserInfo userInfo = null;
        final long token = Binder.clearCallingIdentity();
        try {
            userInfo = mUserManager.getUserInfo(userId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return userInfo != null && userInfo.isManagedProfile();
    }

    private void listenForUserSwitches() {
        try {
            ActivityManager.getService().registerUserSwitchObserver(
                new SynchronousUserSwitchObserver() {
                    @Override
                    public void onUserSwitching(int newUserId) throws RemoteException {
                        mHandler.obtainMessage(MSG_USER_SWITCHING, newUserId, 0 /* unused */)
                                .sendToTarget();
                    }
                }, TAG);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to listen for user switching event" ,e);
        }
    }

    /***
     * @param opPackageName the name of the calling package
     * @return authenticator id for the calling user
     */
    public long getAuthenticatorId(String opPackageName) {
        final int userId = getUserOrWorkProfileId(opPackageName, UserHandle.getCallingUserId());
        return mAuthenticatorIds.getOrDefault(userId, 0L);
    }
}
