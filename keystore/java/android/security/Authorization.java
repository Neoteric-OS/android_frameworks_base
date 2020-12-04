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

package android.security;

import android.annotation.NonNull;
import android.hardware.keymint.HardwareAuthToken;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.security.authorizations.IKeystoreAuthorization;
import android.system.keystore2.ResponseCode;
import android.util.Log;

/**
 * @hide This is the client side for IKeystoreAuthorization AIDL.
 */
public class Authorization {
    private static final String TAG = "KeystoreAuthorization";
    private IKeystoreAuthorization mKeystoreAuthorization;

    public static final int SYSTEM_ERROR = ResponseCode.SYSTEM_ERROR;

    public Authorization() {
        mKeystoreAuthorization = null;
    }

    private synchronized IKeystoreAuthorization getService() {
        if (mKeystoreAuthorization == null) {
            mKeystoreAuthorization = IKeystoreAuthorization.Stub.asInterface(
                    ServiceManager.getService("android.security.authorizations"));
        }
        return mKeystoreAuthorization;
    }

    /**
     * Adds an auth token to keystore2.
     *
     * @param authToken created by Android authenticators.
     * @return 0 if successful or {@code ResponseCode.SYSTEM_ERROR}.
     */
    public int addAuthToken(@NonNull HardwareAuthToken authToken) {
        try {
            getService().addAuthToken(authToken);
            //TODO: Clarify the recommended approach of error handling at client side.
            return 0;
        } catch (RemoteException e) {
            Log.w(TAG, "Can not connect to keystore", e);
            return SYSTEM_ERROR;
        } catch (ServiceSpecificException e) {
            return e.errorCode;
        }
    }


}
