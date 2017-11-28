/*
 * Copyright 2017 The Android Open Source Project
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
import android.annotation.CallSuper;
import android.telephony.RadioNetworkConstants;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of data service. Services that extend DataServiceBase must register the service in
 * their AndroidManifest to be detected by the framework. They must be protected by the permission
 * "android.permission.BIND_DATA_SERVICE". The data service definition in the manifest must follow
 * the following format:
 * ...
 * <service android:name=".xxxDataService"
 *     android:permission="android.permission.BIND_DATA_SERVICE" >
 *     <intent-filter>
 *         <action android:name="android.telephony.data.DataService" />
 *     </intent-filter>
 * </service>
 * @hide
 */
@SystemApi
public abstract class DataServiceBase extends Service {

    public static final String DATA_SERVICE_INTERFACE = "android.telephony.data.DataService";
    public static final String DATA_SERVICE_EXTRA_SLOT_ID = "slot_id";

    private final String mTag;

    private static final int DATA_SERVICE_INTERNAL_REQUEST_INITIALIZE_SERVICE          = 1;
    private static final int DATA_SERVICE_REQUEST_SETUP_DATA_CALL                      = 2;
    private static final int DATA_SERVICE_REQUEST_DEACTIVATE_DATA_CALL                 = 3;
    private static final int DATA_SERVICE_REQUEST_SET_INITIAL_ATTACH_APN               = 4;
    private static final int DATA_SERVICE_REQUEST_SET_DATA_PROFILE                     = 5;
    private static final int DATA_SERVICE_REQUEST_GET_DATA_CALL_LIST                   = 6;
    private static final int DATA_SERVICE_REQUEST_REGISTER_DATA_CALL_LIST_CHANGED      = 7;
    private static final int DATA_SERVICE_REQUEST_UNREGISTER_DATA_CALL_LIST_CHANGED    = 8;
    private static final int DATA_SERVICE_INDICATION_DATA_CALL_LIST_CHANGED            = 9;

    private final HandlerThread mHandlerThread;

    private final DataServiceHandler mHandler;

    private final SparseArray<DataServiceImpl> mServiceMap = new SparseArray<>();

    private final SparseArray<IDataServiceWrapper> mBinderMap = new SparseArray<>();

    /**
     * The abstract class of the actual data service implementation. The data service provider
     * must extend this class to support data connection. Note that each instance of data service
     * is associated with one physical SIM slot.
     */
    public class DataServiceImpl {

        private final String mTag;

        private final int mSlotId;

        private final List<IDataServiceCallback> mDataCallListChangedCallbacks = new ArrayList<>();

        public DataServiceImpl(String tag, int slotId) {
            mSlotId = slotId;
            mTag = tag + "-" + (slotId + 1);
        }

        /**
         * @return SIM slot id the data service associated with.
         */
        protected final int getSlotId() {
            return mSlotId;
        }

        /**
         * Setup a data connection. The data service provider must implement this method to support
         * establishing a packet data connection. When completed or error, the service must invoke
         * the provided callback to notify the platform.
         *
         * @param accessNetwork Access network that the data call will be established on. Should be
         *                      one of {@link RadioNetworkConstants.AccessNetworks}.
         *
         * @param dataProfile Data profile used for data call setup. See {@link DataProfile}
         * @param isRoaming True if the device is data roaming.
         * @param allowRoaming True if data roaming is allowed by the user.
         * @param isHandover True if the request is for IWLAN handover.
         * @param callback The result callback for this request.
         */
        protected void setupDataCall(int accessNetwork, DataProfile dataProfile,
                                              boolean isRoaming, boolean allowRoaming,
                                              boolean isHandover, DataServiceCallback callback) {
            // The default implementation is to return unsupported.
            callback.onSetupDataCallComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
        }

        /**
         * Deactivate a data connection. The data service provider must implement this method to
         * support data connection tear down. When completed or error, the service must invoke the
         * provided callback to notify the platform.
         *
         * @param cid Call id returned in the callback of {@link DataServiceImpl#setupDataCall(int,
         * DataProfile, boolean, boolean, boolean, DataServiceCallback)}
         * @param reasonRadioShutDown True if the deactivate request reason is device shut down.
         * @param isHandover True if the request is for IWLAN handover.
         * @param callback The result callback for this request.
         */
        protected void deactivateDataCall(int cid, boolean reasonRadioShutDown,
                                                   boolean isHandover,
                                                   DataServiceCallback callback) {
            // The default implementation is to return unsupported.
            callback.onSetInitialAttachApnComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
        }

        /**
         * Set an APN to initial attach network.
         *
         * @param dataProfile Data profile used for data call setup. See {@link DataProfile}
         * @param isRoaming True if the device is data roaming.
         * @param callback The result callback for this request.
         */
        protected void setInitialAttachApn(DataProfile dataProfile, boolean isRoaming,
                                           DataServiceCallback callback) {
            // The default implementation is to return unsupported.
            callback.onSetInitialAttachApnComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
        }

        /**
         * Send current carrier's data profiles to the data service.
         *
         * @param dps A list of data profiles.
         * @param isRoaming True if the device is data roaming.
         * @param callback The result callback for this request.
         */
        protected void setDataProfile(List<DataProfile> dps, boolean isRoaming,
                                      DataServiceCallback callback) {
            // The default implementation is to return unsupported.
            callback.onSetDataProfileComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
        }

        /**
         * Get the active data call list
         *
         * @param callback The result callback for this request.
         */
        protected void getDataCallList(DataServiceCallback callback) {
            // The default implementation is to return unsupported.
            callback.onGetDataCallListComplete(DataServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
        }

        private void registerForDataCallListChanged(IDataServiceCallback callback) {
            synchronized (mDataCallListChangedCallbacks) {
                mDataCallListChangedCallbacks.add(callback);
            }
        }

        private void unregisterForDataCallListChanged(IDataServiceCallback callback) {
            synchronized (mDataCallListChangedCallbacks) {
                mDataCallListChangedCallbacks.remove(callback);
            }
        }

        /**
         * Notify the current data call list changed. Data service must invoke this method whenever
         * there is any data call status changed.
         *
         * @param dataCallList List of the current active data call.
         */
        protected final void notifyDataCallListChanged(List<DataCallResponse> dataCallList) {
            synchronized (mDataCallListChangedCallbacks) {
                for (IDataServiceCallback callback : mDataCallListChangedCallbacks) {
                    mHandler.obtainMessage(DATA_SERVICE_INDICATION_DATA_CALL_LIST_CHANGED, mSlotId,
                            0, new DataCallListChangedIndication(dataCallList, callback))
                            .sendToTarget();
                }
            }
        }

        /**
         * Called when the instance of data service is destroyed (e.g. got unbind or binder died).
         */
        @CallSuper
        protected void onDestroy() {
            mDataCallListChangedCallbacks.clear();
        }

        protected final void log(String s) {
            Rlog.d(mTag, s);
        }

        protected final void loge(String s) {
            Rlog.e(mTag, s);
        }
    }

    private static final class SetupDataCallRequest {
        final int mAccessNetwork;
        final DataProfile mDataProfile;
        final boolean mIsRoaming;
        final boolean mAllowRoaming;
        final boolean mIsHandover;
        final IDataServiceCallback mCallback;
        SetupDataCallRequest(int accessNetwork, DataProfile dataProfile, boolean isRoaming,
                             boolean allowRoaming, boolean isHandover,
                             IDataServiceCallback callback) {
            mAccessNetwork = accessNetwork;
            mDataProfile = dataProfile;
            mIsRoaming = isRoaming;
            mAllowRoaming = allowRoaming;
            mIsHandover = isHandover;
            mCallback = callback;
        }
    }

    private static final class DeactivateDataCallRequest {
        final int mCid;
        final boolean mReasonRadioShutDown;
        final boolean mIsHandover;
        final IDataServiceCallback mCallback;
        DeactivateDataCallRequest(int cid, boolean reasonRadioShutDown, boolean isHandover,
                                  IDataServiceCallback callback) {
            mCid = cid;
            mReasonRadioShutDown = reasonRadioShutDown;
            mIsHandover = isHandover;
            mCallback = callback;
        }
    }

    private static final class SetInitialAttachApnRequest {
        final DataProfile mDataProfile;
        final boolean mIsRoaming;
        final IDataServiceCallback mCallback;
        SetInitialAttachApnRequest(DataProfile dataProfile, boolean isRoaming,
                                   IDataServiceCallback callback) {
            mDataProfile = dataProfile;
            mIsRoaming = isRoaming;
            mCallback = callback;
        }
    }

    private static final class SetDataProfileRequest {
        final List<DataProfile> mDps;
        final boolean mIsRoaming;
        final IDataServiceCallback mCallback;
        SetDataProfileRequest(List<DataProfile> dps, boolean isRoaming,
                              IDataServiceCallback callback) {
            mDps = dps;
            mIsRoaming = isRoaming;
            mCallback = callback;
        }
    }

    private static final class DataCallListChangedIndication {
        final List<DataCallResponse> mDataCallList;
        final IDataServiceCallback mCallback;
        DataCallListChangedIndication(List<DataCallResponse> dataCallList,
                                      IDataServiceCallback callback) {
            mDataCallList = dataCallList;
            mCallback = callback;
        }
    }

    private class DataServiceHandler extends Handler {

        DataServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            IDataServiceCallback callback;
            final int slotId = message.arg1;
            DataServiceImpl service;

            synchronized (mServiceMap) {
                service = mServiceMap.get(slotId);
            }

            switch (message.what) {
                case DATA_SERVICE_INTERNAL_REQUEST_INITIALIZE_SERVICE:
                    service = createDataService(message.arg1);
                    if (service != null) {
                        mServiceMap.put(slotId, service);
                    }
                    break;
                case DATA_SERVICE_REQUEST_SETUP_DATA_CALL:
                    if (service == null) break;
                    SetupDataCallRequest setupDataCallRequest = (SetupDataCallRequest) message.obj;
                    service.setupDataCall(setupDataCallRequest.mAccessNetwork,
                            setupDataCallRequest.mDataProfile, setupDataCallRequest.mIsRoaming,
                            setupDataCallRequest.mAllowRoaming, setupDataCallRequest.mIsHandover,
                            new DataServiceCallback(setupDataCallRequest.mCallback));

                    break;
                case DATA_SERVICE_REQUEST_DEACTIVATE_DATA_CALL:
                    if (service == null) break;
                    DeactivateDataCallRequest deactivateDataCallRequest =
                            (DeactivateDataCallRequest) message.obj;
                    service.deactivateDataCall(deactivateDataCallRequest.mCid,
                            deactivateDataCallRequest.mReasonRadioShutDown,
                            deactivateDataCallRequest.mIsHandover,
                            new DataServiceCallback(deactivateDataCallRequest.mCallback));
                    break;
                case DATA_SERVICE_REQUEST_SET_INITIAL_ATTACH_APN:
                    if (service == null) break;
                    SetInitialAttachApnRequest setInitialAttachApnRequest =
                            (SetInitialAttachApnRequest) message.obj;
                    service.setInitialAttachApn(setInitialAttachApnRequest.mDataProfile,
                            setInitialAttachApnRequest.mIsRoaming,
                            new DataServiceCallback(setInitialAttachApnRequest.mCallback));
                    break;
                case DATA_SERVICE_REQUEST_SET_DATA_PROFILE:
                    if (service == null) break;
                    SetDataProfileRequest setDataProfileRequest =
                            (SetDataProfileRequest) message.obj;
                    service.setDataProfile(setDataProfileRequest.mDps,
                            setDataProfileRequest.mIsRoaming,
                            new DataServiceCallback(setDataProfileRequest.mCallback));
                    break;
                case DATA_SERVICE_REQUEST_GET_DATA_CALL_LIST:
                    if (service == null) break;

                    service.getDataCallList(new DataServiceCallback(
                            (IDataServiceCallback) message.obj));
                    break;
                case DATA_SERVICE_REQUEST_REGISTER_DATA_CALL_LIST_CHANGED:
                    if (service == null) break;
                    service.registerForDataCallListChanged((IDataServiceCallback) message.obj);
                    break;
                case DATA_SERVICE_REQUEST_UNREGISTER_DATA_CALL_LIST_CHANGED:
                    if (service == null) break;
                    callback = (IDataServiceCallback) message.obj;
                    service.unregisterForDataCallListChanged(callback);
                    break;
                case DATA_SERVICE_INDICATION_DATA_CALL_LIST_CHANGED:
                    if (service == null) break;
                    DataCallListChangedIndication indication =
                            (DataCallListChangedIndication) message.obj;
                    try {
                        indication.mCallback.onDataCallListChanged(indication.mDataCallList);
                    } catch (RemoteException e) {
                        loge("Failed to call onDataCallListChanged. " + e);
                    }
                    break;
            }
        }
    }

    protected DataServiceBase(String tag) {
        mTag = tag;

        mHandlerThread = new HandlerThread(mTag);
        mHandlerThread.start();

        mHandler = new DataServiceHandler(mHandlerThread.getLooper());
        log("Data service created");
    }

    /**
     * Create the actual implementation of data service object {@link DataServiceImpl}.
     *
     * @param slotId SIM slot id the data service associated with.
     * @return Data service object
     */
    protected abstract DataServiceImpl createDataService(int slotId);

    /** @hide */
    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !DATA_SERVICE_INTERFACE.equals(intent.getAction())) {
            loge("Unexpected intent " + intent);
            return null;
        }

        int slotId = intent.getIntExtra(
                DATA_SERVICE_EXTRA_SLOT_ID, SubscriptionManager.INVALID_SIM_SLOT_INDEX);

        if (!SubscriptionManager.isValidSlotIndex(slotId)) {
            loge("Invalid slot id " + slotId);
            return null;
        }

        log("onBind: slot id=" + slotId);

        IDataServiceWrapper binder = mBinderMap.get(slotId);
        if (binder == null) {
            Message msg = mHandler.obtainMessage(DATA_SERVICE_INTERNAL_REQUEST_INITIALIZE_SERVICE);
            msg.arg1 = slotId;
            msg.sendToTarget();

            binder = new IDataServiceWrapper(slotId);
            mBinderMap.put(slotId, binder);
        }

        return binder;
    }

    /** @hide */
    @Override
    public boolean onUnbind(Intent intent) {
        int slotId = intent.getIntExtra(DATA_SERVICE_EXTRA_SLOT_ID,
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        if (mBinderMap.get(slotId) != null) {
            DataServiceImpl serviceImpl;
            synchronized (mServiceMap) {
                serviceImpl = mServiceMap.get(slotId);
            }
            if (serviceImpl != null) {
                serviceImpl.onDestroy();
            }
            mBinderMap.remove(slotId);
        }

        // If all clients unbinds, quit the handler thread
        if (mBinderMap.size() == 0) {
            mHandlerThread.quit();
        }

        return false;
    }

    /** @hide */
    @Override
    public void onDestroy() {
        synchronized (mServiceMap) {
            for (int i = 0; i < mServiceMap.size(); i++) {
                DataServiceImpl serviceImpl = mServiceMap.get(i);
                if (serviceImpl != null) {
                    serviceImpl.onDestroy();
                }
            }
            mServiceMap.clear();
        }

        mHandlerThread.quit();
    }

    /**
     * A wrapper around IDataService that forwards calls to implementations of
     * {@link DataServiceBase}.
     */
    private class IDataServiceWrapper extends IDataService.Stub {

        private final int mSlotId;

        IDataServiceWrapper(int slotId) {
            mSlotId = slotId;
        }

        @Override
        public void setupDataCall(int accessNetwork, DataProfile dataProfile,
                                  boolean isRoaming, boolean allowRoaming, boolean isHandover,
                                  IDataServiceCallback callback) {
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_SETUP_DATA_CALL, mSlotId, 0,
                    new SetupDataCallRequest(accessNetwork, dataProfile, isRoaming, allowRoaming,
                            isHandover, callback)).sendToTarget();
        }

        @Override
        public void deactivateDataCall(int cid, boolean reasonRadioShutDown, boolean isHandover,
                                       IDataServiceCallback callback) {
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_DEACTIVATE_DATA_CALL, mSlotId, 0,
                    new DeactivateDataCallRequest(cid, reasonRadioShutDown, isHandover, callback))
                    .sendToTarget();
        }

        @Override
        public void setInitialAttachApn(DataProfile dataProfile, boolean isRoaming,
                                        IDataServiceCallback callback) {
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_SET_INITIAL_ATTACH_APN, mSlotId, 0,
                    new SetInitialAttachApnRequest(dataProfile, isRoaming, callback))
                    .sendToTarget();
        }

        @Override
        public void setDataProfile(List<DataProfile> dps, boolean isRoaming,
                                   IDataServiceCallback callback) {
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_SET_DATA_PROFILE, mSlotId, 0,
                    new SetDataProfileRequest(dps, isRoaming, callback)).sendToTarget();
        }

        @Override
        public void getDataCallList(IDataServiceCallback callback) {
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_GET_DATA_CALL_LIST, mSlotId, 0,
                    callback).sendToTarget();
        }

        @Override
        public void registerForDataCallListChanged(IDataServiceCallback callback) {
            if (callback == null) {
                loge("Callback is null");
                return;
            }
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_REGISTER_DATA_CALL_LIST_CHANGED, mSlotId,
                    0, callback).sendToTarget();
        }

        @Override
        public void unregisterForDataCallListChanged(IDataServiceCallback callback) {
            if (callback == null) {
                loge("Callback is null");
                return;
            }
            mHandler.obtainMessage(DATA_SERVICE_REQUEST_UNREGISTER_DATA_CALL_LIST_CHANGED, mSlotId,
                    0, callback).sendToTarget();
        }
    }

    protected final void log(String s) {
        Rlog.d(mTag, s);
    }

    protected final void loge(String s) {
        Rlog.e(mTag, s);
    }
}
