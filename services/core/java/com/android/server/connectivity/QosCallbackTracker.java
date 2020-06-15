/*
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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.net.ConnectivityManager;
import android.net.IQosCallback;
import android.net.NetworkAgent;
import android.net.QosCallbackException;
import android.net.QosFilter;
import android.net.QosSession;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.telephony.EpsBearerQosSessionAttributes;
import android.util.Slog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tracks qos callbacks and handles the communication between the network agent and application.
 *
 * Any method prefixed by handle must be called from the ConnectivityService handler thread.
 *
 * @hide
 */
public class QosCallbackTracker {
    private static final String TAG = QosCallbackTracker.class.getSimpleName();
    private final Handler mConnectivityServiceHandler;
    private static final boolean DBG = true;

    /**
     * Each agent gets a unique callback id that is used to proxy messages back to the original
     * callback.
     */
    private int mAgentCallbackIdCounter = 1;

    @NonNull private final Map<Integer, QosCallbackAgentConnection> mCallbackIdsToConnections =
            new HashMap<>();
    @NonNull private final Map<IBinder, QosCallbackAgentConnection> mCallbacksToConnections =
            new HashMap<>();
    @NonNull private final Map<NetworkAgentInfo, Map<Integer, QosCallbackAgentConnection>>
            mNetworkAgentsToConnections = new HashMap<>();


    private QosFilterValidator mValidator;

    @NonNull private final Consumer<Integer> mEnforceRequestCountLimit;
    @NonNull private final Consumer<Integer> mDecrementNetworkRequestPerUidCount;

    public QosCallbackTracker(@NonNull final Handler handler,
            @NonNull final QosFilterValidator validator,
            @NonNull final Consumer<Integer> enforceRequestCountLimit,
            @NonNull final Consumer<Integer> decrementNetworkRequestPerUidCount) {
        mConnectivityServiceHandler = handler;
        mValidator = validator;
        mEnforceRequestCountLimit = enforceRequestCountLimit;
        mDecrementNetworkRequestPerUidCount = decrementNetworkRequestPerUidCount;
    }

    /**
     * Registers the callback with the tracker
     * @param networkAgentInfo the info of the corresponding network agent
     * @param callback the callback to register
     * @param filter the filter being registered alongside the callback
     */
    public void registerCallback(@NonNull final NetworkAgentInfo networkAgentInfo,
            @NonNull final IQosCallback callback, @NonNull final QosFilter filter) {
        int uid = Binder.getCallingUid();
        mConnectivityServiceHandler.post(() ->
                handleRegisterCallback(networkAgentInfo, callback, filter, uid)
        );
    }

    private void handleRegisterCallback(@NonNull final NetworkAgentInfo networkAgentInfo,
            @NonNull final IQosCallback callback, @NonNull final QosFilter filter, int uid) {

        IBinder binder = callback.asBinder();
        if (mCallbacksToConnections.get(binder) != null) {
            logwtf("handleRegisterCallback: Callback should never be registered twice");
            return;
        }

        int newCallbackId = mAgentCallbackIdCounter++;
        QosCallbackAgentConnection ac = new QosCallbackAgentConnection(newCallbackId,
                networkAgentInfo, callback, filter, uid);

        // Checks the request count limit.
        try {
            mEnforceRequestCountLimit.accept(uid);
        }  catch (ServiceSpecificException ex) {
            if (ex.errorCode == ConnectivityManager.Errors.TOO_MANY_REQUESTS) {
                ac.sendEventQosSessionError(
                        QosCallbackException.EX_TYPE_TOO_MANY_REQUESTS, "");
            } else {
                logwtf("handleRegisterCallback: unknown ServiceSpecificException exception");
            }
            return;
        }

        // Only add to the callback maps if the NetworkAgent successfully registered it
        if (ac.sendCmdRegisterCallback()) {
            mCallbackIdsToConnections.put(newCallbackId, ac);
            mCallbacksToConnections.put(binder, ac);

            Map<Integer, QosCallbackAgentConnection> subMap =
                    mNetworkAgentsToConnections.computeIfAbsent(networkAgentInfo, (k) ->
                            new HashMap<>()
                    );
            subMap.put(newCallbackId, ac);


            if (DBG) log("handleRegisterCallback: added callback " + newCallbackId);
        } else {
            if (DBG) log("handleRegisterCallback: error sending register callback");

            // There was an issue with registering with the agent and so we need to decrement
            // the uid counter.
            mDecrementNetworkRequestPerUidCount.accept(ac.mUid);
        }
    }

    /**
     * Unregisters callback
     * @param callback callback to unregister
     */
    public void unregisterCallback(@NonNull final IQosCallback callback) {
        mConnectivityServiceHandler.post(() -> handleUnregisterCallback(callback, true));
    }

    private void handleUnregisterCallback(@NonNull final IQosCallback callback,
            boolean sendToNetworkAgent) {
        IBinder binder = callback.asBinder();
        QosCallbackAgentConnection agentConnection = mCallbacksToConnections.get(binder);
        if (agentConnection != null) {
            if (DBG) {
                log("handleUnregisterCallback: unregister " + agentConnection.mAgentCallbackId);
            }

            mDecrementNetworkRequestPerUidCount.accept(agentConnection.mUid);
            mCallbacksToConnections.remove(binder);
            mCallbackIdsToConnections.remove(agentConnection.mAgentCallbackId);

            Map<Integer, QosCallbackAgentConnection> subMap =
                    mNetworkAgentsToConnections.get(agentConnection.mNetworkAgentInfo);
            if (subMap == null) {
                subMap.remove(agentConnection.mAgentCallbackId);
                if (subMap.size() == 0) {
                    mNetworkAgentsToConnections.remove(agentConnection.mNetworkAgentInfo);
                }
            } else {
                logwtf("handleUnregisterCallback: No entry for network agent info");
            }

            if (sendToNetworkAgent) {
                agentConnection.sendCmdUnregisterCallback();
            }
            agentConnection.unlinkToDeathRecipient();
        } else {
            logw("handleUnregisterCallback: agentConnection is null");
        }
    }

    /**
     * Called when the NetworkAgent sends the qos session available event
     *
     * Note: Must be called on the connectivity service handler thread
     *
     * @param msg wraps the qos session attributes
     */
    public void handleEventQosSessionAvailable(@NonNull final Message msg) {
        QosCallbackAgentConnection ac = getConnection(msg, "handleEventQosSessionAvailable: ");
        if (ac != null) {
            ac.sendEventQosSessionAvailable((Bundle) msg.obj);
        }
    }

    /**
     * Called when the NetworkAgent sends the qos session lost event
     *
     * Note: Must be called on the connectivity service handler thread
     *
     * @param msg wraps the qos session attributes
     */
    public void handleEventQosSessionLost(@NonNull final Message msg) {
        QosCallbackAgentConnection ac = getConnection(msg, "handleEventQosSessionLost: ");
        if (ac != null) {
            ac.sendEventQosSessionLost((QosSession) msg.obj);
        }
    }

    /**
     * Called when the NetworkAgent sends the qos session on error event
     *
     * Note: Must be called on the connectivity service handler thread
     *
     * @param msg wraps the qos session attributes
     */
    public void handleEventQosSessionError(@NonNull final Message msg) {
        QosCallbackAgentConnection ac = getConnection(msg, "handleEventQosSessionError: ");
        if (ac != null) {
            ac.sendEventQosSessionError(msg.arg2, "");
            handleUnregisterCallback(ac.mCallback, false);
        }
    }

    /**
     * Unregisters all callbacks associated to this network agent
     *
     * @param networkAgentInfo
     */
    public void handleNetworkReleased(@NonNull final NetworkAgentInfo networkAgentInfo) {
        Map<Integer, QosCallbackAgentConnection> subMap =
                mNetworkAgentsToConnections.get(networkAgentInfo);
        if (subMap != null) {
            QosCallbackAgentConnection[] agentConnections =
                    subMap.values().toArray(new QosCallbackAgentConnection[0]);
            for (QosCallbackAgentConnection agentConnection : agentConnections) {
                agentConnection.sendEventQosSessionError(
                        QosCallbackException.EX_TYPE_FILTER_NETWORK_RELEASED, "");

                // Call usual unregister workflow w\o sending anything to network agent since it
                // is disconnected.
                handleUnregisterCallback(agentConnection.mCallback, false);
            }
        } else {
            logwtf("handleNetworkLost: No entry for network agent info");
        }
    }

    private QosCallbackAgentConnection getConnection(@NonNull Message msg,
            @NonNull String logPrefix) {
        QosCallbackAgentConnection ac = mCallbackIdsToConnections.get(msg.arg1);
        if (ac == null) {
            loge(logPrefix + ": " + " missing callback id");
        }
        return ac;
    }

    // Must be instantiated on the handler thread.
    private class QosCallbackAgentConnection implements IBinder.DeathRecipient {

        private final int mAgentCallbackId;
        @NonNull private final NetworkAgentInfo mNetworkAgentInfo;
        @NonNull private final IQosCallback mCallback;
        @NonNull private final IBinder mBinder;
        @NonNull private final QosFilter mFilter;
        private boolean mBinderDied;
        private final int mUid;

        public int getUid() {
            return mUid;
        }

        QosCallbackAgentConnection(final int agentCallbackId,
                @NonNull final NetworkAgentInfo networkAgentInfo,
                @NonNull final IQosCallback callback,
                @NonNull final QosFilter filter,
                final int uid) {
            mAgentCallbackId = agentCallbackId;
            mNetworkAgentInfo = networkAgentInfo;
            mCallback = callback;
            mFilter = filter;
            mUid = uid;
            mBinder = mCallback.asBinder();
            mBinderDied = false;
        }

        @Override
        public void binderDied() {
            if (!mBinderDied) {
                logw("binderDied: binder died with callback id: " + mAgentCallbackId);
                unregisterCallback(mCallback);
                mBinderDied = true;
            }
        }

        private boolean isBinderDead() {
            if (mBinderDied) {
                return true;
            } else if (!mBinder.isBinderAlive()) {
                // This is probably unnecessary since #binderDied will catch this.  But, it could
                // prevent unnecessary remote exceptions from being called depending on when
                // #binderDied is called.
                binderDied();
                return true;
            } else {
                return false;
            }
        }

        public void unlinkToDeathRecipient() {
            mBinder.unlinkToDeath(this, 0);
        }


        // Returns false if the NetworkAgent was never notified.
        public boolean sendCmdRegisterCallback() {
            Optional<Integer> exceptionType = mValidator.validate(mFilter, mNetworkAgentInfo);
            if (!exceptionType.isPresent()) {
                Message msg = new Message();
                msg.what = NetworkAgent.CMD_REGISTER_QOS_CALLBACK;
                msg.arg1 = mAgentCallbackId;
                msg.obj = mFilter;
                try {
                    mBinder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    loge("failed linking to death recipient", e);
                    return false;
                }
                mNetworkAgentInfo.asyncChannel.sendMessage(msg);
                return true;
            } else {
                try {
                    if (mBinder.isBinderAlive()) {
                        if (DBG) log("sendCmdRegisterCallback: filter validation failed");
                        this.mCallback.onError(exceptionType.get(), "");
                    }
                } catch (RemoteException e) {
                    loge("sendCmdRegisterCallback:", e);
                }
                return false;
            }
        }

        public void sendCmdUnregisterCallback() {
            Message msg = new Message();
            msg.what = NetworkAgent.CMD_UNREGISTER_QOS_CALLBACK;
            msg.arg1 = mAgentCallbackId;
            if (DBG) log("sendCmdUnregisterCallback: filter validation failed");
            mNetworkAgentInfo.asyncChannel.sendMessage(msg);
        }

        public void sendEventQosSessionAvailable(@NonNull Bundle bundle) {
            final QosSession session = bundle.getParcelable("session");
            if (session == null) {
                logwtf("sendEventQosSessionAvailable: missing session in bundle");
            } else if (session.getSessionType() == QosSession.TYPE_EPS_BEARER) {
                EpsBearerQosSessionAttributes attributes = bundle.getParcelable("attributes");
                try {
                    if (!isBinderDead()) {
                        if (DBG) log("sendEventQosSessionAvailable: sending...");
                        mCallback.onQosEpsBearerSessionAvailable(session, attributes);
                    }
                } catch (RemoteException e) {
                    loge("sendEventQosSessionAvailable: remote exception", e);
                }
            } else {
                logwtf("sendEventQosSessionAvailable: no case for session type "
                        + session.getSessionType());
            }
        }

        public void sendEventQosSessionLost(@NonNull QosSession session) {
            try {
                if (!isBinderDead()) {
                    if (DBG) log("sendEventQosSessionLost: sending...");
                    mCallback.onQosSessionLost(session);
                }
            } catch (RemoteException e) {
                loge("sendEventQosSessionLost: remote exception", e);
            }
        }

        public void sendEventQosSessionError(int type, String message) {
            try {
                if (!isBinderDead()) {
                    if (DBG) log("sendEventQosSessionError: sending...");
                    mCallback.onError(type, message);
                }
            } catch (RemoteException e) {
                loge("sendEventQosSessionError: remote exception", e);
            }
        }
    }

    private static void log(String msg) {
        Slog.d(TAG, msg);
    }

    private static void logw(String msg) {
        Slog.w(TAG, msg);
    }

    private static void loge(String msg) {
        Slog.e(TAG, msg);
    }

    private static void loge(String msg, Throwable t) {
        Slog.e(TAG, msg, t);
    }

    private static void logwtf(String msg) {
        Slog.wtf(TAG, msg);
    }
}
