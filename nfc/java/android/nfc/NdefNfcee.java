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
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class can be used to read/write T4T Ndef data.
 * Refer to the NFC forum specification NDEF-NFCEE section for more details.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_NFC_OEM_EXTENSION)
@SystemApi
public final class NdefNfcee {
    private static final String TAG = "NdefNfcee";
    static NdefNfcee sNdefNfcee;

    private NdefNfcee() {
    }

    /**
     * Helper to get an instance of this class.
     *
     * @return
     * @hide
     */
    @NonNull
    public static NdefNfcee getInstance() {
        if (sNdefNfcee == null) {
            sNdefNfcee = new NdefNfcee();
        }
        return sNdefNfcee;
    }

    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data is successful.
     */
    public static final int WRITE_DATA_STATUS_SUCCESS = 0;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to unknown reasons.
     */
    public static final int WRITE_DATA_STATUS_FAILED = -1;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to ongoing rf activity.
     */
    public static final int WRITE_DATA_ERROR_RF_ACTIVATED = -2;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to Nfc off.
     */
    public static final int WRITE_DATA_ERROR_NFC_NOT_ON = -3;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to invalid file id.
     */
    public static final int WRITE_DATA_ERROR_INVALID_FILE_ID = -4;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to invalid length.
     */
    public static final int WRITE_DATA_ERROR_INVALID_LENGTH = -5;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to core connection create failure.
     */
    public static final int WRITE_DATA_ERROR_CONNECTION_FAILED = -6;
    /**
     * Return flag for {@link #writeData(int, byte[])}.
     * It indicates write data fail due to empty payload.
     */
    public static final int WRITE_DATA_ERROR_EMPTY_PAYLOAD = -7;
    /**
     * Returns flag for {@link #writeData(int, byte[])}.
     * It idicates write data fail due to invalid ndef format.
     */
    public static final int WRITE_DATA_ERROR_NDEF_VALIDATION_FAILED = -8;

    /**
     * Possible return values for {@link #writeData(int, byte[])}.
     *
     * @hide
     */
    @IntDef(prefix = { "WRITE_DATA_" }, value = {
        WRITE_DATA_STATUS_SUCCESS,
        WRITE_DATA_STATUS_FAILED,
        WRITE_DATA_ERROR_RF_ACTIVATED,
        WRITE_DATA_ERROR_NFC_NOT_ON,
        WRITE_DATA_ERROR_INVALID_FILE_ID,
        WRITE_DATA_ERROR_INVALID_LENGTH,
        WRITE_DATA_ERROR_CONNECTION_FAILED,
        WRITE_DATA_ERROR_EMPTY_PAYLOAD,
        WRITE_DATA_ERROR_NDEF_VALIDATION_FAILED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WriteDataStatus{}

    /**
     * This API performs writes of T4T data to Nfcee.
     *
     * <p>This is an I/O operation and will block until complete. It must
     * not be called from the main application thread.
     *
     * @param fileId File id (Refer NFC Forum Type 4 Tag Specification
     *               Section 4.2 File Identifiers and Access Conditions
     *               for more information) to which to write.
     * @param data   This should be valid Ndef Message format.
     *               Refer to Nfc forum NDEF specification NDEF Message section
     * @return status of the operation.
     * @hide
     */
    @SystemApi
    @WorkerThread
    @NonNull
    public @WriteDataStatus int writeData(@NonNull @IntRange(from = 0, to = 65535) int fileId,
            @NonNull byte[] data) {
        return NfcAdapter.callServiceReturn(() ->
                NfcAdapter.sNdefNfceeService.writeData(fileId, data), WRITE_DATA_STATUS_FAILED);
    }

    /**
     * This API performs reading of T4T content of Nfcee.
     *
     * <p>This is an I/O operation and will block until complete. It must
     * not be called from the main application thread.
     *
     * @param fileId File Id (Refer NFC Forum Type 4 Tag Specification
     *               Section 4.2 File Identifiers and Access Conditions
     *               for more information) from which to read.
     *
     * @return - Returns Ndef message if success
     *           Refer to Nfc forum NDEF specification NDEF Message section
     * @throws IllegalStateException if read fails
     * @hide
     */
    @SystemApi
    @WorkerThread
    @NonNull
    public byte[] readData(@NonNull @IntRange(from = 0, to = 65535) int fileId) {
        return NfcAdapter.callServiceReturn(() ->
            NfcAdapter.sNdefNfceeService.readData(fileId), null);
    }

    /**
     * Return flag for {@link #clearNdefData()}.
     * It indicates clear data is successful.
     */
    public static final int CLEAR_DATA_STATUS_SUCCESS = 1;
     /**
     * Return flag for {@link #clearNdefData()}.
     * It indicates clear data failed.
     */
    public static final int CLEAR_DATA_STATUS_FAILED = 0;

    /**
     * Possible return values for {@link #clearNdefData()}.
     *
     * @hide
     */
    @IntDef(prefix = { "CLEAR_DATA_" }, value = {
        CLEAR_DATA_STATUS_SUCCESS,
        CLEAR_DATA_STATUS_FAILED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ClearDataStatus{}

    /**
     * This API will set all the T4T NDEF NFCEE data to zero.
     *
     * <p>This is an I/O operation and will block until complete. It must
     * not be called from the main application thread.
     *
     * <p>This API can be called regardless of NDEF file lock state.
     * </p>
     * @return status of the operation
     *
     * @hide
     */
    @SystemApi
    @WorkerThread
    public @ClearDataStatus int clearNdefData() {
        return NfcAdapter.callServiceReturn(() ->
            NfcAdapter.sNdefNfceeService.clearNdefData(), CLEAR_DATA_STATUS_FAILED);
    }

    /**
     * Returns whether NDEF NFCEE operation is ongoing or not.
     *
     * @return true if NDEF NFCEE operation is ongoing, else false.
     * @hide
     */
    @SystemApi
    public boolean isNdefOperationOngoing() {
        return NfcAdapter.callServiceReturn(() ->
            NfcAdapter.sNdefNfceeService.isNdefOperationOngoing(), false);
    }

    /**
     * This Api is to check the status of NDEF NFCEE emulation feature is
     * supported or not.
     *
     * @return true if NDEF NFCEE emulation feature is supported, else false.
     * @hide
     */
    @SystemApi
    public boolean isNdefNfceeEmulationSupported() {
        return NfcAdapter.callServiceReturn(() ->
            NfcAdapter.sNdefNfceeService.isNdefNfceeEmulationSupported(), false);
    }

    /**
     * This API performs reading of T4T NDEF NFCEE CC file content.
     *
     * @return read bytes :-Returns CC file content if success
     *         null if failed to read
     * @hide
     */
    @SystemApi
    @WorkerThread
    @Nullable
    public NdefCcFileInfo readCcfile() {
        return NfcAdapter.callServiceReturn(() ->
            NfcAdapter.sNdefNfceeService.readCcfile(), null);
    }
}
