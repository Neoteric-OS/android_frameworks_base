/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.IntDef;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.euicc.EuiccProfileInfo;
import android.util.Log;

import com.android.internal.telephony.euicc.IDeleteProfileCallback;
import com.android.internal.telephony.euicc.IDisableProfileCallback;
import com.android.internal.telephony.euicc.IEuiccCardController;
import com.android.internal.telephony.euicc.IGetAllProfilesCallback;
import com.android.internal.telephony.euicc.IGetDefaultSmdpAddressCallback;
import com.android.internal.telephony.euicc.IGetProfileCallback;
import com.android.internal.telephony.euicc.IGetSmdsAddressCallback;
import com.android.internal.telephony.euicc.IResetMemoryCallback;
import com.android.internal.telephony.euicc.ISetDefaultSmdpAddressCallback;
import com.android.internal.telephony.euicc.ISetNicknameCallback;
import com.android.internal.telephony.euicc.ISwitchToProfileCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * EuiccCardManager is the application interface to an eSIM card.
 *
 * @hide
 *
 * TODO(b/35851809): Make this a SystemApi.
 */
public class EuiccCardManager {
    private static final String TAG = "EuiccCardManager";

    /** Options for resetting eUICC memory */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = { "RESET_OPTION_" }, value = {
            RESET_OPTION_DELETE_OPERATIONAL_PROFILES,
            RESET_OPTION_DELETE_FIELD_LOADED_TEST_PROFILES,
            RESET_OPTION_RESET_DEFAULT_SMDP_ADDRESS
    })
    public @interface ResetOption {}

    /** Deletes all operational profiles. */
    public static final int RESET_OPTION_DELETE_OPERATIONAL_PROFILES = 1;

    /** Deletes all field-loaded testing profiles. */
    public static final int RESET_OPTION_DELETE_FIELD_LOADED_TEST_PROFILES = 1 << 1;

    /** Resets the default SM-DP+ address. */
    public static final int RESET_OPTION_RESET_DEFAULT_SMDP_ADDRESS = 1 << 2;

    /** Result code of execution with no error. */
    public static final int RESULT_OK = 0;

    /**
     * Callback to receive the result of an eUICC card API.
     *
     * @param <T> Type of the result.
     */
    public interface ResultCallback<T> {
        /**
         * This method will be called when an eUICC card API call is completed.
         *
         * @param resultCode This can be {@link #RESULT_OK} or other positive values returned by the
         *     eUICC.
         * @param result The result object. It can be null if the {@code resultCode} is not
         *     {@link #RESULT_OK}.
         */
        void onComplete(int resultCode, T result);
    }

    private final Context mContext;

    /** @hide */
    public EuiccCardManager(Context context) {
        mContext = context;
    }

    private IEuiccCardController getIEuiccCardController() {
        return IEuiccCardController.Stub.asInterface(
                ServiceManager.getService("euicc_card_controller"));
    }

    /**
     * Gets all the profiles on eUicc.
     *
     * @param callback The callback to get the result code and all the profiles.
     */
    public void getAllProfiles(ResultCallback<EuiccProfileInfo[]> callback) {
        try {
            getIEuiccCardController().getAllProfiles(mContext.getOpPackageName(),
                    new IGetAllProfilesCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode, EuiccProfileInfo[] profiles) {
                            callback.onComplete(resultCode, profiles);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling getAllProfiles", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the profile of the given iccid.
     *
     * @param iccid The iccid of the profile.
     * @param callback The callback to get the result code and profile.
     */
    public void getProfile(String iccid, ResultCallback<EuiccProfileInfo> callback) {
        try {
            getIEuiccCardController().getProfile(mContext.getOpPackageName(), iccid,
                    new IGetProfileCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode, EuiccProfileInfo profile) {
                            callback.onComplete(resultCode, profile);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling getProfile", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Disables the profile of the given iccid.
     *
     * @param iccid The iccid of the profile.
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the result code.
     */
    public void disableProfile(String iccid, boolean refresh, ResultCallback<Void> callback) {
        try {
            getIEuiccCardController().disableProfile(mContext.getOpPackageName(), iccid, refresh,
                    new IDisableProfileCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode) {
                            callback.onComplete(resultCode, null);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling disableProfile", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Switches from the current profile to another profile. The current profile will be disabled
     * and the specified profile will be enabled.
     *
     * @param iccid The iccid of the profile to switch to.
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the result code and the EuiccProfileInfo enabled.
     */
    public void switchToProfile(String iccid, boolean refresh,
            ResultCallback<EuiccProfileInfo> callback) {
        try {
            getIEuiccCardController().switchToProfile(mContext.getOpPackageName(), iccid, refresh,
                    new ISwitchToProfileCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode, EuiccProfileInfo profile) {
                            callback.onComplete(resultCode, profile);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling switchToProfile", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @return The EID.
     */
    public String getEid() {
        try {
            return getIEuiccCardController().getEid();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling getEid", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the nickname of the profile of the given iccid.
     *
     * @param iccid The iccid of the profile.
     * @param nickname The nickname of the profile.
     * @param callback The callback to get the result code.
     */
    public void setNickname(String iccid, String nickname, ResultCallback<Void> callback) {
        try {
            getIEuiccCardController().setNickname(mContext.getOpPackageName(), iccid, nickname,
                    new ISetNicknameCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode) {
                            callback.onComplete(resultCode, null);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling setNickname", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Deletes the profile of the given iccid from eUICC.
     *
     * @param iccid The iccid of the profile.
     * @param callback The callback to get the result code.
     */
    public void deleteProfile(String iccid, ResultCallback<Void> callback) {
        try {
            getIEuiccCardController().deleteProfile(mContext.getOpPackageName(), iccid,
                    new IDeleteProfileCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode) {
                            callback.onComplete(resultCode, null);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling deleteProfile", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Resets the eUICC memory.
     *
     * @param options Bits of the options of resetting which parts of the eUICC memory. See
     *     EuiccCard for details.
     * @param callback The callback to get the result code.
     */
    public void resetMemory(@ResetOption int options, ResultCallback<Void> callback) {
        try {
            getIEuiccCardController().resetMemory(mContext.getOpPackageName(), options,
                    new IResetMemoryCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode) {
                            callback.onComplete(resultCode, null);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling resetMemory", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result code and the default SM-DP+ address.
     */
    public void getDefaultSmdpAddress(ResultCallback<String> callback) {
        try {
            getIEuiccCardController().getDefaultSmdpAddress(mContext.getOpPackageName(),
                    new IGetDefaultSmdpAddressCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode, String address) {
                            callback.onComplete(resultCode, address);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling getDefaultSmdpAddress", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result code and the SM-DS address.
     */
    public void getSmdsAddress(ResultCallback<String> callback) {
        try {
            getIEuiccCardController().getSmdsAddress(mContext.getOpPackageName(),
                    new IGetSmdsAddressCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode, String address) {
                            callback.onComplete(resultCode, address);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling getSmdsAddress", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the default SM-DP+ address of eUICC.
     *
     * @param defaultSmdpAddress The default SM-DP+ address to set.
     * @param callback The callback to get the result code.
     */
    public void setDefaultSmdpAddress(String defaultSmdpAddress, ResultCallback<Void> callback) {
        try {
            getIEuiccCardController().setDefaultSmdpAddress(mContext.getOpPackageName(),
                    defaultSmdpAddress,
                    new ISetDefaultSmdpAddressCallback.Stub() {
                        @Override
                        public void onComplete(int resultCode) {
                            callback.onComplete(resultCode, null);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling setDefaultSmdpAddress", e);
            throw e.rethrowFromSystemServer();
        }
    }
}
