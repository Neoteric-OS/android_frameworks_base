/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.telephony;

/**
 * Provides basic structure for platform to connect to the WAP push manager service.
 * <p>
 * <code>
 * WapPushManager wapPushManager = new WapPushManagerImpl();
 * if (wapPushManager.bindToWapPushManagerService(context, carrierPackageName)) {
 *   // wait for onServiceReady callback
 * } else {
 *   // Unable to bind: handle error.
 * }
 * </code>
 * <p>Upon completion {@link #disposeConnection} should be called to unbind the service.
 * @hide
 */
public abstract class WapPushManager {
    private volatile WapPushManagerConnection mWapPushManagerConnection;
    private volatile IWapPushManager mIWapPushManager;

    /**
     * The {@link android.content.Intent} that must be declared as handled by the
     * WAP push manager service.
     */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.telephony.WapPushManager";

    /** Returns the package name of WAP push manager service application. */
    @NonNull
    public String getWapPushManagerServicePackage() {
        return "com.android.smspush";
    }

    /**
     * Binds to the WAP push manager service. This method should be called exactly once.
     *
     * @param context the context
     * @param carrierPackageName the service's package name
     * @return {@code true} upon successfully binding to a service, {@code false} otherwise
     */
    public boolean bindToWapPushManagerService(@NonNull Context context) {
        Preconditions.checkState(mWapPushManagerConnection == null);

        Intent intent = new Intent(CarrierMessagingService.SERVICE_INTERFACE);
        intent.setPackage(getWapPushManagerServicePackage());
        mWapPushManagerConnection = new WapPushManagerConnection();
        return context.bindService(intent, mWapPushManagerConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbinds the WAP push manager service. This method should be called exactly once.
     *
     * @param context the context
     */
    public void unbindWapPushManagerService(@NonNull Context context) {
        Preconditions.checkNotNull(mWapPushManagerConnection);
        context.unbindService(mWapPushManagerConnection);
        mWapPushManagerConnection = null;
    }
}
