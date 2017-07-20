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

package android.telephony;

import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.mbms.MbmsException;
import android.telephony.mbms.MbmsStreamingManagerCallback;
import android.telephony.mbms.MbmsUtils;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceCallback;
import android.telephony.mbms.StreamingServiceInfo;
import android.telephony.mbms.vendor.IMbmsStreamingService;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

/**
 * This class provides functionality for streaming media over MBMS.
 */
public class MbmsStreamingManager {
    private static final String LOG_TAG = "MbmsStreamingManager";

    /**
     * Service action which must be handled by the middleware implementing the MBMS streaming
     * interface.
     * @hide
     */
    @SystemApi
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String MBMS_STREAMING_SERVICE_ACTION =
            "android.telephony.action.EmbmsStreaming";

    private static boolean sIsInitialized = false;

    private AtomicReference<IMbmsStreamingService> mService = new AtomicReference<>(null);
    private final MbmsStreamingManagerCallback mCallbackToApp;
    private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST, "Received death notification");
        }
    };

    private final Context mContext;
    private int mSubscriptionId = INVALID_SUBSCRIPTION_ID;

    /** @hide */
    private MbmsStreamingManager(Context context, MbmsStreamingManagerCallback callback,
                    int subscriptionId) {
        mContext = context;
        mCallbackToApp = callback;
        mSubscriptionId = subscriptionId;
    }

    /**
     * Create a new MbmsStreamingManager using the given subscription ID.
     *
     * Note that this call will bind a remote service. You may not call this method on your app's
     * main thread. This may throw an {@link MbmsException}, indicating errors that may happen
     * during the initialization or binding process.
     *
     *
     * You may only have one instance of {@link MbmsStreamingManager} per UID. If you call this
     * method while there is an active instance of {@link MbmsStreamingManager} in your process
     * (in other words, one that has not had {@link #dispose()} called on it), this method will
     * throw an {@link MbmsException}. If you call this method in a different process
     * running under the same UID, an error will be indicated via
     * {@link MbmsStreamingManagerCallback#error(int, String)}.
     *
     * Note that initialization may fail asynchronously. If you wish to try again after you
     * receive such an asynchronous error, you must call dispose() on the instance of
     * {@link MbmsStreamingManager} that you received before calling this method again.
     *
     * @param context The {@link Context} to use.
     * @param callback A callback object on which you wish to receive results of asynchronous
     *                 operations.
     * @param subscriptionId The subscription ID to use.
     */
    public static MbmsStreamingManager create(Context context,
            MbmsStreamingManagerCallback callback, int subscriptionId)
            throws MbmsException {
        if (sIsInitialized) {
            throw new MbmsException(MbmsException.InitializationErrors.ERROR_DUPLICATE_INITIALIZE);
        }
        MbmsStreamingManager manager = new MbmsStreamingManager(context, callback, subscriptionId);
        manager.bindAndInitialize();
        sIsInitialized = true;
        return manager;
    }

    /**
     * Create a new MbmsStreamingManager using the system default data subscription ID.
     * See {@link #create(Context, MbmsStreamingManagerCallback, int)}.
     */
    public static MbmsStreamingManager create(Context context,
            MbmsStreamingManagerCallback callback)
            throws MbmsException {
        return create(context, callback, SubscriptionManager.getDefaultSubscriptionId());
    }

    /**
     * Terminates this instance, ending calls to the registered listener.  Also terminates
     * any streaming services spawned from this instance.
     *
     * May throw an {@link IllegalStateException}
     */
    public void dispose() {
        try {
            IMbmsStreamingService streamingService = mService.get();
            if (streamingService == null) {
                // Ignore and return, assume already disposed.
                return;
            }
            streamingService.dispose(mSubscriptionId);
        } catch (RemoteException e) {
            // Ignore for now
        } finally {
            mService.set(null);
            sIsInitialized = false;
        }
    }

    /**
     * An inspection API to retrieve the list of streaming media currently be advertised.
     * The results are returned asynchronously through the previously registered callback.
     * serviceClasses lets the app filter on types of programming and is opaque data between
     * the app and the carrier.
     *
     * Multiple calls replace the list of serviceClasses of interest.
     *
     * This may throw an {@link MbmsException} containing any error in
     * {@link android.telephony.mbms.MbmsException.GeneralErrors},
     * {@link MbmsException#ERROR_MIDDLEWARE_NOT_BOUND}, or
     * {@link MbmsException#ERROR_MIDDLEWARE_LOST}.
     *
     * May also throw an unchecked {@link IllegalArgumentException} or an
     * {@link IllegalStateException}
     *
     * @param classList A list of streaming service classes that the app would like updates on.
     */
    public void getStreamingServices(List<String> classList) throws MbmsException {
        IMbmsStreamingService streamingService = mService.get();
        if (streamingService == null) {
            throw new MbmsException(MbmsException.ERROR_MIDDLEWARE_NOT_BOUND);
        }
        try {
            int returnCode = streamingService.getStreamingServices(mSubscriptionId, classList);
            if (returnCode != MbmsException.SUCCESS) {
                throw new MbmsException(returnCode);
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Remote process died");
            mService.set(null);
            throw new MbmsException(MbmsException.ERROR_MIDDLEWARE_LOST);
        }
    }

    /**
     * Starts streaming a requested service, reporting status to the indicated listener.
     * Returns an object used to control that stream. The stream may not be ready for consumption
     * immediately upon return from this method -- wait until the streaming state has been
     * reported via
     * {@link android.telephony.mbms.StreamingServiceCallback#streamStateUpdated(int, int)}
     *
     * May throw an
     * {@link MbmsException} containing any of the error codes in
     * {@link android.telephony.mbms.MbmsException.GeneralErrors},
     * {@link MbmsException#ERROR_MIDDLEWARE_NOT_BOUND}, or
     * {@link MbmsException#ERROR_MIDDLEWARE_LOST}.
     *
     * May also throw an {@link IllegalArgumentException} or an {@link IllegalStateException}
     *
     * Asynchronous errors through the listener include any of the errors in
     * {@link android.telephony.mbms.MbmsException.GeneralErrors} or
     * {@link android.telephony.mbms.MbmsException.StreamingErrors}.
     *
     * @param serviceInfo The information about the service to stream.
     * @param listener A listener that'll be called when something about the stream changes.
     * @return An instance of {@link StreamingService} through which the stream can be controlled.
     */
    public StreamingService startStreaming(StreamingServiceInfo serviceInfo,
            StreamingServiceCallback listener) throws MbmsException {
        IMbmsStreamingService streamingService = mService.get();
        if (streamingService == null) {
            throw new MbmsException(MbmsException.ERROR_MIDDLEWARE_NOT_BOUND);
        }

        try {
            int returnCode = streamingService.startStreaming(
                    mSubscriptionId, serviceInfo.getServiceId(), listener);
            if (returnCode != MbmsException.SUCCESS) {
                throw new MbmsException(returnCode);
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Remote process died");
            mService.set(null);
            throw new MbmsException(MbmsException.ERROR_MIDDLEWARE_LOST);
        }

        return new StreamingService(mSubscriptionId, streamingService, serviceInfo, listener);
    }

    private void bindAndInitialize() throws MbmsException {
        MbmsUtils.startBinding(mContext, MBMS_STREAMING_SERVICE_ACTION,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        IMbmsStreamingService streamingService =
                                IMbmsStreamingService.Stub.asInterface(service);
                        int result;
                        try {
                            result = streamingService.initialize(mCallbackToApp, mSubscriptionId);
                        } catch (RemoteException e) {
                            Log.e(LOG_TAG, "Service died before initialization");
                            sendErrorToApp(
                                    MbmsException.InitializationErrors.ERROR_UNABLE_TO_INITIALIZE,
                                    e.toString());
                            sIsInitialized = false;
                            return;
                        } catch (RuntimeException e) {
                            Log.e(LOG_TAG, "Runtime exception during initialization");
                            sendErrorToApp(
                                    MbmsException.InitializationErrors.ERROR_UNABLE_TO_INITIALIZE,
                                    e.toString());
                            sIsInitialized = false;
                            return;
                        }
                        if (result != MbmsException.SUCCESS) {
                            sendErrorToApp(result, "Error returned during initialization");
                            sIsInitialized = false;
                            return;
                        }
                        try {
                            streamingService.asBinder().linkToDeath(mDeathRecipient, 0);
                        } catch (RemoteException e) {
                            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST,
                                    "Middleware lost during initialization");
                            sIsInitialized = false;
                            return;
                        }
                        mService.set(streamingService);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        sIsInitialized = false;
                        mService.set(null);
                    }
                });
    }

    private void sendErrorToApp(int errorCode, String message) {
        try {
            mCallbackToApp.error(errorCode, message);
        } catch (RemoteException e) {
            // Ignore, should not happen locally.
        }
    }
}
