/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.annotation.IntDef;
import android.content.Context;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth BAP Broadcast Source profile.
 *
 * <p>BluetoothBapBroadcastSource is a proxy object for controlling the Bluetooth BAP Broadcast
 * Source Service via IPC. Use {@link BluetoothAdapter#getProfileProxy}
 * to get the BluetoothBapBroadcastSource proxy object.
 *
 * @hide
 */

public final class BluetoothBapBroadcastSource implements BluetoothProfile {
    private static final String TAG = "BluetoothBapBroadcastSource";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private final Callback mCallback;

    @IntDef(prefix = {"BROADCAST_STATE_"}, value = {
      BROADCAST_STATE_DISABLED,
      BROADCAST_STATE_ENABLING,
      BROADCAST_STATE_ENABLED,
      BROADCAST_STATE_DISABLING,
      BROADCAST_STATE_STREAMING,
      BROADCAST_STATE_PLAYING,
      BROADCAST_STATE_NOT_PLAYING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastState {}

    public static final int BROADCAST_STATE_DISABLED = 10;
    public static final int BROADCAST_STATE_ENABLING = 11;
    public static final int BROADCAST_STATE_ENABLED = 12;
    public static final int BROADCAST_STATE_DISABLING = 13;
    public static final int BROADCAST_STATE_STREAMING = 14;
    public static final int BROADCAST_STATE_PLAYING = 15;
    public static final int BROADCAST_STATE_NOT_PLAYING = 16;

    /**
     * Interface for receiving events related to broadcasts
     */
    public interface Callback {
        /**
         * Called when broadcast state has changed
         *
         * @param prevState broadcast state before the change
         * @param newState broadcast state after the change
         */
        @BroadcastState
        void onBroadcastStateChange(int prevState, int newState);
        /**
         * Called when encryption key has been updated
         *
         * @param success true if the key was updated successfully, false otherwise
         */
        void onEncryptionKeySet(boolean success);
    }

    private BluetoothAdapter mAdapter;

    /**
     * Create a BluetoothBapBroadcastSource proxy object for interacting with the local
     * BAP Bluetooth Broadcast Source service.
     *
     * @hide
     */
    /*package*/ BluetoothBapBroadcastSource(Context context,
                                            BluetoothProfile.ServiceListener listener) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /*
     * @hide
     */
    /*package*/ void close() {
    }

    @Override
    protected void finalize() {
        // The empty finalize needs to be kept or the
        // cts signature tests would fail.
    }

    /**
     * Not supported - please use {@link BluetoothManager#getConnectedDevices(int)}
     * with {@link BluetoothProfile#GATT} as argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public int getConnectionState(BluetoothDevice device) {
        throw new UnsupportedOperationException(
                   "Use BluetoothManager#getConnectedDevices instead.");
    }

    /**
     * Not supported - please use {@link BluetoothManager#getConnectedDevices(int)}
     * with {@link BluetoothProfile#GATT} as argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        throw new UnsupportedOperationException(
                   "Use BluetoothManager#getConnectedDevices instead.");
    }

    /**
     * Not supported - please use {@link BluetoothManager#getConnectedDevices(int)}
     * with {@link BluetoothProfile#GATT} as argument
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        throw new UnsupportedOperationException(
                   "Use BluetoothManager#getConnectedDevices instead.");
    }

    /**
     * Enable broadcast mode.
     *
     * Generates a new broadcast ID and enables sending of encrypted or unencrypted
     * isochronous PDUs
     *
     *
     * @return {@link BluetoothStatusCodes.SUCCESS} on success,
     *         {@link BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_BROADCAST_MODE_FAILED} on
     *         failure
     * @hide
     */
    public int enableBroadcastMode() {
        if (DBG) log("enableBroadcastMode");
        return BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_BROADCAST_MODE_FAILED;
    }

    /**
     * Disable broadcast mode.
     *
     * @return {@link BluetoothStatusCodes.SUCCESS} on success,
     *         {@link BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_BROADCAST_MODE_FAILED} on
     *         failure
     * @hide
     */
    public int disableBroadcastMode() {
        if (DBG) log("disableBroadcastMode");
        return BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_BROADCAST_MODE_FAILED;
    }

    /**
     * Get the current broadcast state
     *
     * @return {@link BroadcastState}
     *
     * @hide
     */
    @BroadcastState
    public int getBroadcastState() {
        if (DBG) log("getBroadcastState");
        return BROADCAST_STATE_DISABLED;
    }

    /**
     * Enable or disable broadcast encryption
     *
     * @param enable true if encryption should be enabled, false otherwise
     * @param encLen 0 if encryption is disabled, 4 bytes (low security), 16 bytes (high security)
     * @param useExisting true if an already specified key should be used, false otherwise
     *
     * @return {@link BluetoothStatusCodes#SUCCESS} on success,
     *         {@link BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_ENCRYTION_KEY_FAILED} on
     *         failure
     *
     * @hide
     */
    public int setEncryption(boolean enable, int encLen, boolean useExisting) {
        if (DBG) log("setEncryption");
        return BluetoothStatusCodes.ERROR_BAP_BROADCAST_SOURCE_SET_ENCRYTION_KEY_FAILED;
    }

    /**
     * Get the encryption key that was set before
     *
     * @return encryption key as a byte array or null if no encryption key was set
     *
     * @hide
     */
    public byte[] getEncryptionKey() {
        if (DBG) log("getEncryptionKey");
        return null;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
