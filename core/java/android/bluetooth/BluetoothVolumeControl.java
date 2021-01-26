/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package android.bluetooth;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.CloseGuard;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth Volume Control service.
 *
 * <p>BluetoothVolumeControl is a proxy object for controlling the Bluetooth VC
 * Service via IPC. Use {@link BluetoothAdapter#getProfileProxy} to get
 * the BluetoothVolumeControl proxy object.
 *
 */
public final class BluetoothVolumeControl implements BluetoothProfile, AutoCloseable {
    private static final String TAG = "BluetoothVolumeControl";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private CloseGuard mCloseGuard;

    /**
     * Intent used to broadcast the change in connection state of the Volume Control
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     * <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile.</li>
     * <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_VC_CONNECTION_STATE_CHANGED =
            "android.bluetooth.action.VC_CONNECTION_STATE_CHANGED";

    private BluetoothAdapter mAdapter;
    private final BluetoothProfileConnector<IBluetoothVolumeControl> mProfileConnector =
            new BluetoothProfileConnector(this, BluetoothProfile.VOLUME_CONTROL, TAG,
                    IBluetoothVolumeControl.class.getName()) {
                @Override
                public IBluetoothVolumeControl getServiceInterface(IBinder service) {
                    return IBluetoothVolumeControl.Stub.asInterface(Binder.allowBlocking(service));
                }
            };

    /**
     * Create a BluetoothVolumeControl proxy object for interacting with the local
     * Bluetooth Volume Control service.
     */
    /*package*/ BluetoothVolumeControl(Context context, ServiceListener listener) {
        if (DBG)
            log("Create BluetoothVolumeControl proxy object");

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mProfileConnector.connect(context, listener);
        mCloseGuard = new CloseGuard();
        mCloseGuard.open("close");
    }

    protected void finalize() {
        if (mCloseGuard != null) {
            mCloseGuard.warnIfOpen();
        }
        close();
    }

    /*package*/ public void close() { mProfileConnector.disconnect(); }

    private IBluetoothVolumeControl getService() { return mProfileConnector.getService(); }

    /**
     * Initiate connection to a profile of the remote bluetooth device.
     *
     * <p> This API returns false in scenarios like the profile on the
     * device is already connected or Bluetooth is not turned on.
     * When this API returns true, it is guaranteed that
     * connection state intent for the profile will be broadcasted with
     * the state. Users can get the connection state of the profile
     * from this intent.
     *
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean connect(@Nullable BluetoothDevice device) {
        if (DBG)
            log("connect(" + device + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled() && isValidDevice(device)) {
                return service.connect(device);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * Initiate disconnection from a profile
     *
     * <p> This API will return false in scenarios like the profile on the
     * Bluetooth device is not in connected state etc. When this API returns,
     * true, it is guaranteed that the connection state change
     * intent will be broadcasted with the state. Users can get the
     * disconnection state of the profile from this intent.
     *
     * <p> If the disconnection is initiated by a remote device, the state
     * will transition from {@link #STATE_CONNECTED} to
     * {@link #STATE_DISCONNECTED}. If the disconnect is initiated by the
     * host (local) device the state will transition from
     * {@link #STATE_CONNECTED} to state {@link #STATE_DISCONNECTING} to
     * state {@link #STATE_DISCONNECTED}. The transition to
     * {@link #STATE_DISCONNECTING} can be used to distinguish between the
     * two scenarios.
     *
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean disconnect(@Nullable BluetoothDevice device) {
        if (DBG)
            log("disconnect(" + device + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled() && isValidDevice(device)) {
                return service.disconnect(device);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public @NonNull List<BluetoothDevice> getConnectedDevices() {
        if (VDBG)
            log("getConnectedDevices()");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled()) {
                return service.getConnectedDevices();
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public @NonNull List<BluetoothDevice> getDevicesMatchingConnectionStates(
            @NonNull int[] states) {
        if (VDBG)
            log("getDevicesMatchingConnectionStates()");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled()) {
                return service.getDevicesMatchingConnectionStates(states);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public @BluetoothProfile.BtProfileState int getConnectionState(
            @Nullable BluetoothDevice device) {
        if (VDBG)
            log("getConnectionState(" + device + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled() && isValidDevice(device)) {
                return service.getConnectionState(device);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Tells remote device to set an absolute volume.
     *
     * @param volume Absolute volume to be set on remote device.
     *               Minimum value is 0 and maximum value is 255
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public void setVolume(@Nullable BluetoothDevice device, int volume) {
        if (DBG)
            log("setVolume(" + volume + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled()) {
                service.setVolume(device, volume);
                return;
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
        }
    }

    /**
     * Set connection policy of the profile
     *
     * <p> The device should already be paired.
     * Connection policy can be one of {@link #CONNECTION_POLICY_ALLOWED},
     * {@link #CONNECTION_POLICY_FORBIDDEN}, {@link #CONNECTION_POLICY_UNKNOWN}
     *
     *
     * @param device Paired bluetooth device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true if connectionPolicy is set, false on error
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(
            @Nullable BluetoothDevice device, @ConnectionPolicy int connectionPolicy) {
        if (VDBG)
            log("setConnectionPolicy(" + device + ", " + connectionPolicy + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled() && isValidDevice(device)) {
                if (connectionPolicy != BluetoothProfile.CONNECTION_POLICY_FORBIDDEN
                        && connectionPolicy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
                    return false;
                }
                return service.setConnectionPolicy(device, connectionPolicy);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return false;
        }
    }

    /**
     * Get the connection policy of the profile.
     *
     * <p> The connection policy can be any of:
     * {@link #CONNECTION_POLICY_ALLOWED}, {@link #CONNECTION_POLICY_FORBIDDEN},
     * {@link #CONNECTION_POLICY_UNKNOWN}
     *
     *
     * @param device Bluetooth device
     * @return connection policy of the device
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)
    public @ConnectionPolicy int getConnectionPolicy(@Nullable BluetoothDevice device) {
        if (VDBG)
            log("getConnectionPolicy(" + device + ")");
        final IBluetoothVolumeControl service = getService();
        try {
            if (service != null && isEnabled() && isValidDevice(device)) {
                return service.getConnectionPolicy(device);
            }
            if (service == null)
                Log.w(TAG, "Proxy not attached to service");
            return BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
        }
    }

    @UnsupportedAppUsage
    private boolean isEnabled() {
        return mAdapter.getState() == BluetoothAdapter.STATE_ON;
    }

    @UnsupportedAppUsage
    private static boolean isValidDevice(@Nullable BluetoothDevice device) {
        return device != null && BluetoothAdapter.checkBluetoothAddress(device.getAddress());
    }

    @UnsupportedAppUsage
    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
