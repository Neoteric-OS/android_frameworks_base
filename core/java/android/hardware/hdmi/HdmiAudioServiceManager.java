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

package android.hardware.hdmi;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresFeature;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.StringDef;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.sysprop.HdmiProperties;
import android.util.ArrayMap;
import android.util.Log;


/**
 * The {@link HdmiControlManager} class is used to send HDMI control messages
 * to attached CEC devices.
 *
 * <p>Provides various HDMI client instances that represent HDMI-CEC logical devices
 * hosted in the system. {@link #getTvClient()}, for instance will return an
 * {@link HdmiTvClient} object if the system is configured to host one. Android system
 * can host more than one logical CEC devices. If multiple types are configured they
 * all work as if they were independent logical devices running in the system.
 *
 * @hide
 */

@SystemService(Context.HDMI_AUDIO_SERVICE)
@RequiresFeature(PackageManager.FEATURE_HDMI_CEC)
public final class HdmiAudioServiceManager {
    private static final String TAG = "HdmiAudioServiceManager";

    @Nullable private final IHdmiAudioService mService;

    /**
     * {@hide} - hide this constructor because it has a parameter of type IHdmiControlService,
     * which is a system private class. The right way to create an instance of this class is
     * using the factory Context.getSystemService.
     */
    public HdmiAudioServiceManager(IHdmiAudioService service) {
        mService = service;
        if (mService != null) {
            Log.e(TAG, "Get HDMI Audio Service successfully.");
        }else{
            Log.e(TAG, "HDMI Audio Service is null!");
        }
    }

    /**
     * Get current HDMI eARC Status
     *
     * @hide
     */
    public int getEarcStatus() {
        try{
            return mService.getEarcStatus();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }


    /**
     * Set eARC raw capabilities for test
     *
     * @hide
     */
    @NonNull
    public String setEarcRawCaps(@NonNull byte[] rawCaps) {
        if (mService == null) {
            Log.e(TAG, "HdmiAudioService is not available");
            throw new RuntimeException("HdmiAudioService is not available");
        }
        try{
            return mService.setEarcRawCaps(rawCaps);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}