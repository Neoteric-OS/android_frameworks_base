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

package android.bluetooth.le;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth Broadcast
 * profile.
 *
 * <p>BluetoothLeBroadcast is a proxy object for controlling the Bluetooth
 * Broadcast Service via IPC. Use {@link BluetoothAdapter#getProfileProxy}
 * to get the BluetoothLeBroadcast proxy object.
 *
 * @hide
 */

public final class BluetoothLeBroadcast implements BluetoothProfile {
    private static final String TAG = "BluetoothLeBroadcast";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    /**
     * Intent used to broadcast the change in broadcast state.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     * <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile.</li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_Disabled}, {@link #Enabling},
     * {@link #STATE_ENABLED}, {@link #STATE_DISABLING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_BROADCAST_STATE_CHANGED =
            "android.bluetooth.broadcast.profile.action.BROADCAST_STATE_CHANGED";

    /**
     * Intent used to broadcast the change in broadcast audio state.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current audio state . </li>
     * <li> {@link #EXTRA_PREVIOUS_STATE}- The previous audio state.</li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_PLAYING}, {@link #STATE_NOT_PLAYING},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_BROADCAST_AUDIO_STATE_CHANGED =
            "android.bluetooth.broadcast.profile.action.BROADCAST_AUDIO_STATE_CHANGED";

    /**
     * Intent used to broadcast encryption key generation status.
     *
     * <p>This intent will have 2 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current audio state . </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} can be any of
     * {@link #TRUE}, {@link #FALSE},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_BROADCAST_ENCRYPTION_KEY_GENERATED =
            "android.bluetooth.broadcast.profile.action.BROADCAST_ENCRYPTION_KEY_GENERATED";

    public static final int STATE_DISABLED = 10;
    public static final int STATE_ENABLING = 11;
    public static final int STATE_ENABLED = 12;
    public static final int STATE_DISABLING = 13;
    public static final int STATE_STREAMING = 14;
    public static final int STATE_PLAYING = 10;
    public static final int STATE_NOT_PLAYING = 11;

    private BluetoothAdapter mAdapter;

    /**
     * Create a BluetoothLeBroadcast proxy object for interacting with the local
     * Bluetooth Broadcast service.
     *
     * @hide
     */
    /*package*/ BluetoothLeBroadcast(Context context, BluetoothProfile.ServiceListener listener) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /*
     * @hide
     */
    /*package*/ void close() {
    }

    @Override
    public void finalize() {
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
     * Enable or disable broadcast mode
     *
     * @hide
     */
    public boolean setBroadcastMode(boolean enable) {
        if (DBG) log("setBroadcastMode");
        return false;
    }

    /**
     * Get broadcast status
     *
     * @hide
     */
    public int getBroadcastStatus() {
        if (DBG) log("getBroadcastStatus");
        return STATE_DISABLED;
    }

    /**
     * Enable or disable encryption
     *
     * @hide
     */
    public boolean setEncryption(boolean enable, int encLen, boolean useExisting) {
        if (DBG) log("setEncryption");
        return false;
    }

    /**
     * Get existing encryption key
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
