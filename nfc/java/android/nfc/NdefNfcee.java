/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.nfc;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class can be used to read/write T4T Ndef data
 * from NDEF NFCEE
 */
@FlaggedApi(Flags.FLAG_NFC_OEM_EXTENSION)
public final class NdefNfcee {
    private static final String TAG = "NdefNfcee";
    static NdefNfcee sNdefNfcee;

    static INdefNfcee sNdefNfceeService;
    NfcAdapter mAdapter;

    protected NdefNfcee(@NonNull NfcAdapter adapter) {
        sNdefNfceeService = adapter.sNdefNfceeService;
        mAdapter = adapter;
    }

    /**
     * Helper to get an instance of this class.
     *
     * @param adapter context of the Application service object
     * @return
     * @hide
     */
    @SystemApi
    @NonNull
    public static NdefNfcee getInstance(@NonNull NfcAdapter adapter) {
        if (sNdefNfcee == null) {
            sNdefNfcee = new NdefNfcee(adapter);
        }
        return sNdefNfcee;
    }

    /**
     * This API performs writes of T4T data to Nfcee.
     *
     * @param fileId File Id to which to write
     * @param data   data bytes to be written
     * @return number of bytes written if success else negative number of
     * error code listed as here .
     * -1  STATUS_FAILED
     * -2  ERROR_RF_ACTIVATED
     * -3  ERROR_NFC_NOT_ON
     * -4  ERROR_INVALID_FILE_ID
     * -5  ERROR_INVALID_LENGTH
     * -6  ERROR_CONNECTION_FAILED
     * -7  ERROR_EMPTY_PAYLOAD
     * -8  ERROR_NDEF_VALIDATION_FAILED
     * @hide
     */
    @SystemApi
    @NonNull
    public int doWriteData(@NonNull byte[] fileId, @NonNull byte[] data) {
        try {
            return sNdefNfceeService.doWriteData(fileId, data);
        } catch (RemoteException e) {
            Log.e(TAG, "NdefNfceeService is not available.");
            mAdapter.attemptDeadServiceRecovery(e);
            return -1;
        }
    }

    /**
     * This API performs reading of T4T content of Nfcee.
     *
     * @param fileId : File Id from which to read
     * @return read bytes :-Returns read message if success
     * Returns null if failed to read
     * Returns 0xFF if file is empty.
     * @hide
     */
    @SystemApi
    @NonNull
    public byte[] doReadData(@NonNull byte[] fileId) {
        try {
            return sNdefNfceeService.doReadData(fileId);
        } catch (RemoteException e) {
            Log.e(TAG, "NdefNfceeService is not available.");
            mAdapter.attemptDeadServiceRecovery(e);
            return null;
        }
    }

    /**
     * This API will set all the T4T NFCEE NDEF data to zero.
     * This API can be called regardless of NDEF file lock state.
     * <p>
     * Return "True" when operation is successful. else "False"
     *
     * @hide
     */
    @SystemApi
    public boolean doClearNdefData() {
        try {
            return sNdefNfceeService.doClearNdefData();
        } catch (RemoteException e) {
            Log.e(TAG, "NdefNfceeService is not available.");
            mAdapter.attemptDeadServiceRecovery(e);
            return false;
        }
    }

    /**
     * This Api to check the status of NDEF NFCEE operation status
     * return's number<br/>
     * 1 - Ok<br/>
     * 2 - Busy, T4T read / write operation is ongoing<br/>
     *
     * @hide
     */
    @SystemApi
    public boolean getNdefNfceeStatus() {
        try {
            return sNdefNfceeService.getNdefNfceeStatus();
        } catch (RemoteException e) {
            Log.e(TAG, "NdefNfceeService is not available.");
            mAdapter.attemptDeadServiceRecovery(e);
            return false;
        }
    }

    /**
     * This Api is to check the status of NDEF NFCEE emulation feature is
     * supported or not <p> return's boolean<br/> true - If the feature is
     * supported<br/> false - if the feature not supported<br/>
     *
     * @hide
     */
    @SystemApi
    public boolean isNdefNfceeEmulationSupported() {
        try {
            return sNdefNfceeService.isNdefNfceeEmulationSupported();
        } catch (RemoteException e) {
            Log.e(TAG, "NdefNfceeService is not available.");
            mAdapter.attemptDeadServiceRecovery(e);
            return false;
        }
    }
}
