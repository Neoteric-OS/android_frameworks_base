/*
 * Copyright 2018 The Android Open Source Project
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

package android.telephony.data;

import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Rlog;
import android.telephony.data.ApnSetting.ApnType;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Base class of the qualified networks service. Services that extend QualifiedNetworksService must
 * register the service in their AndroidManifest to be detected by the framework. They must be
 * protected by the permission "android.permission.BIND_TELEPHONY_QUALIFIED_NETWORKS_SERVICE".
 * The qualified networks service definition in the manifest must follow the following format:
 * ...
 * <service android:name=".xxxQualifiedNetworksService"
 *     android:permission="android.permission.BIND_TELEPHONY_QUALIFIED_NETWORKS_SERVICE" >
 *     <intent-filter>
 *         <action android:name="android.telephony.data.QualifiedNetworksService" />
 *     </intent-filter>
 * </service>
 * @hide
 */
@SystemApi
public abstract class QualifiedNetworksService extends Service {
    private static final String TAG = QualifiedNetworksService.class.getSimpleName();

    public static final String QUALIFIED_NETWORKS_SERVICE_INTERFACE =
            "android.telephony.data.QualifiedNetworksService";

    private static final int QNS_CREATE_NETWORK_AVAILABILITY_UPDATER                = 1;
    private static final int QNS_REMOVE_NETWORK_AVAILABILITY_UPDATER                = 2;
    private static final int QNS_REMOVE_ALL_NETWORK_AVAILABILITY_UPDATERS           = 3;
    private static final int QNS_UPDATE_QUALIFIED_NETWORKS                          = 4;

    private final HandlerThread mHandlerThread;

    private final QualifiedNetworksServiceHandler mHandler;

    private final SparseArray<NetworkAvailabilityUpdater> mUpdaters = new SparseArray<>();

    /** @hide */
    @VisibleForTesting
    public final IQualifiedNetworksServiceWrapper mBinder = new IQualifiedNetworksServiceWrapper();

    /**
     * The abstract class of the network availability updater implementation. The vendor qualified
     * network service must extend this class to report the available networks for data
     * connection setup. Note that each instance of network availability updater is associated with
     * one physical SIM slot.
     */
    public abstract class NetworkAvailabilityUpdater {
        private final int mSlotId;

        private IQualifiedNetworksServiceCallback mCallback;

        /**
         * Qualified networks for each APN type. Key is the {@link ApnType}, value is the array
         * of available networks.
         */
        private SparseArray<int[]> mQualifiedNetworksList = new SparseArray<>();

        /**
         * Constructor
         * @param slotId SIM slot id the network availability updater associated with.
         */
        public NetworkAvailabilityUpdater(int slotId) {
            mSlotId = slotId;
        }

        /**
         * @return SIM slot id the network availability updater associated with.
         */
        public final int getSlotId() {
            return mSlotId;
        }

        private void registerForQualifiedNetworksChanged(
                IQualifiedNetworksServiceCallback callback) {
            mCallback = callback;

            // Force sending the qualified networks upon registered.
            if (mCallback != null) {
                for (int i = 0; i < mQualifiedNetworksList.size(); i++) {
                    try {
                        mCallback.onQualifiedNetworksChanged(mQualifiedNetworksList.keyAt(i),
                                mQualifiedNetworksList.valueAt(i));
                    } catch (RemoteException e) {
                        loge("Failed to call onQualifiedNetworksChanged. " + e);
                    }
                }
            }
        }

        /**
         * Update the qualified networks list. Network availability updater must invoke this method
         * whenever the qualified networks changes. If this method is never invoked for certain
         * APN type, then frameworks will always use the default (i.e. cellular) data and network
         * service.
         *
         * @param apnType APN type of the qualified networks
         * @param qualifiedNetworks List of the qualified networks in the preferred order by the
         * given {@link @apnType}. Each element in the array is a {@link AccessNetworkType}. An
         * empty list or null indicates no networks are qualified for data setup.
         */
        public final void updateQualifiedNetworks(@ApnType int apnType, int[] qualifiedNetworks) {
            mHandler.obtainMessage(QNS_UPDATE_QUALIFIED_NETWORKS, mSlotId, apnType,
                    qualifiedNetworks).sendToTarget();
        }

        private void onUpdateQualifiedNetworks(@ApnType int apnType, int[] qualifiedNetworks) {
            mQualifiedNetworksList.put(apnType, qualifiedNetworks);
            if (mCallback != null) {
                try {
                    mCallback.onQualifiedNetworksChanged(apnType, qualifiedNetworks);
                } catch (RemoteException e) {
                    loge("Failed to call onQualifiedNetworksChanged. " + e);
                }
            }
        }

        /**
         * Called when the qualified networks updater is removed. The extended class can use this
         * method to perform clean up works.
         */
        public abstract void onDestroy();
    }

    private class QualifiedNetworksServiceHandler extends Handler {
        QualifiedNetworksServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            IQualifiedNetworksServiceCallback callback;
            final int slotId = message.arg1;
            NetworkAvailabilityUpdater updater = mUpdaters.get(slotId);

            switch (message.what) {
                case QNS_CREATE_NETWORK_AVAILABILITY_UPDATER:
                    if (mUpdaters.get(slotId) != null) {
                        loge("Network availability updater for slot " + slotId
                                + " already existed.");
                        return;
                    }

                    updater = createNetworkAvailabilityUpdater(slotId);
                    if (updater != null) {
                        mUpdaters.put(slotId, updater);

                        callback = (IQualifiedNetworksServiceCallback) message.obj;
                        updater.registerForQualifiedNetworksChanged(callback);
                    } else {
                        loge("Failed to create network availability updater. slot id = "
                                + slotId);
                    }
                    break;

                case QNS_REMOVE_NETWORK_AVAILABILITY_UPDATER:
                    if (updater != null) {
                        updater.onDestroy();
                        mUpdaters.remove(slotId);
                    }
                    break;

                case QNS_REMOVE_ALL_NETWORK_AVAILABILITY_UPDATERS:
                    for (int i = 0; i < mUpdaters.size(); i++) {
                        updater = mUpdaters.get(i);
                        if (updater != null) {
                            updater.onDestroy();
                        }
                    }
                    mUpdaters.clear();
                    break;

                case QNS_UPDATE_QUALIFIED_NETWORKS:
                    if (updater == null) break;
                    updater.onUpdateQualifiedNetworks(message.arg2, (int[]) message.obj);
                    break;
            }
        }
    }

    /**
     * Default constructor.
     */
    public QualifiedNetworksService() {
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();

        mHandler = new QualifiedNetworksServiceHandler(mHandlerThread.getLooper());
        log("Qualified networks service created");
    }

    /**
     * Create the instance of {@link NetworkAvailabilityUpdater}. Vendor qualified network service
     * must override this method to facilitate the creation of {@link NetworkAvailabilityUpdater}
     * instances. The system will call this method after binding the qualified networks service for
     * each active SIM slot id.
     *
     * @param slotId SIM slot id the qualified networks service associated with.
     * @return Qualified networks service instance
     */
    public abstract NetworkAvailabilityUpdater createNetworkAvailabilityUpdater(int slotId);

    /** @hide */
    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !QUALIFIED_NETWORKS_SERVICE_INTERFACE.equals(intent.getAction())) {
            loge("Unexpected intent " + intent);
            return null;
        }
        return mBinder;
    }

    /** @hide */
    @Override
    public boolean onUnbind(Intent intent) {
        mHandler.obtainMessage(QNS_REMOVE_ALL_NETWORK_AVAILABILITY_UPDATERS).sendToTarget();
        return false;
    }

    /** @hide */
    @Override
    public void onDestroy() {
        mHandlerThread.quit();
    }

    /**
     * A wrapper around IQualifiedNetworksService that forwards calls to implementations of
     * {@link QualifiedNetworksService}.
     */
    private class IQualifiedNetworksServiceWrapper extends IQualifiedNetworksService.Stub {
        @Override
        public void createNetworkAvailabilityUpdater(int slotId,
                                                     IQualifiedNetworksServiceCallback callback) {
            mHandler.obtainMessage(QNS_CREATE_NETWORK_AVAILABILITY_UPDATER, slotId, 0,
                    callback).sendToTarget();
        }

        @Override
        public void removeNetworkAvailabilityUpdater(int slotId) {
            mHandler.obtainMessage(QNS_REMOVE_NETWORK_AVAILABILITY_UPDATER, slotId, 0)
                    .sendToTarget();
        }
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }
}
