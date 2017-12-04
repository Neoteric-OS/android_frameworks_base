/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telephony.euicc;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.telephony.euicc.IEuiccCardController;
import android.service.euicc.IGetAllProfilesCallback;

/**
 * @SystemApi
 * @hide
 */
public class EuiccCardManager {

    private static final String TAG = "EuiccCardManager";
    private final Context mContext;
    private final IEuiccCardController mController;

    /** @hide */
    public EuiccCardManager(Context context) {
        Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - init EuiccCardManager");
        mContext = context;
        mController = IEuiccCardController.Stub.asInterface(ServiceManager.getService("euicc_card_controller"));
        Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - mContext : " + mContext);
        Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - mController : " + mController);
    }

    /**
     *
     * @param eid
     * @param callback
     * @hide
     */
    public void getAllProfiles(String eid, IGetAllProfilesCallback callback) {
        try {
            Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - before call controller get all profiles");
            mController.getAllProfiles(eid, mContext.getOpPackageName(), callback);
            Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - after call controller get all profiles");
        } catch (RemoteException e) {
            Log.e(TAG, "HOLLY DEBUG - EuiccCardManager - remoteException");
            throw e.rethrowFromSystemServer();
        }
    }
}
