/*
 * Copyright 2017 The Android Open Source Project
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
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * This class provides the public APIs to control the Bluetooth Hearing Aid
 * profile.
 *
 * <p>BluetoothHearingAid is a proxy object for controlling the Bluetooth Hearing Aid
 * Service via IPC. Use {@link BluetoothAdapter#getProfileProxy} to get
 * the BluetoothHearingAid proxy object.
 *
 * <p> Each method is protected with its appropriate permission.
 * @hide
 */
public final class BluetoothHearingAid implements BluetoothProfile {
    private static final String TAG = "BluetoothHearingAid";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    /**
     * Intent used to broadcast the change in connection state of the Hearing Aid
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
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED";

    /**
     * Intent used to broadcast the change in the Playing state of the Hearing Aid
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     * <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile. </li>
     * <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_PLAYING}, {@link #STATE_NOT_PLAYING},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PLAYING_STATE_CHANGED =
            "android.bluetooth.hearingaid.profile.action.PLAYING_STATE_CHANGED";

    /**
     * Hearing Aid device is streaming music. This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_PLAYING_STATE_CHANGED} intent.
     */
    public static final int STATE_PLAYING = 10;

    /**
     * Hearing Aid device is NOT streaming music. This state can be one of
     * {@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} of
     * {@link #ACTION_PLAYING_STATE_CHANGED} intent.
     */
    public static final int STATE_NOT_PLAYING = 11;

    private Context mContext;
    private ServiceListener mServiceListener;
    private final ReentrantReadWriteLock mServiceLock = new ReentrantReadWriteLock();
    @GuardedBy("mServiceLock")
    private IBluetoothHearingAid mService;
    private BluetoothAdapter mAdapter;

    private final IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
            new IBluetoothStateChangeCallback.Stub() {
                public void onBluetoothStateChange(boolean up) {
                    if (DBG) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                    if (!up) {
                        if (VDBG) Log.d(TAG, "Unbinding service...");
                        try {
                            mServiceLock.writeLock().lock();
                            mService = null;
                            mContext.unbindService(mConnection);
                        } catch (Exception re) {
                            Log.e(TAG, "", re);
                        } finally {
                            mServiceLock.writeLock().unlock();
                        }
                    } else {
                        try {
                            mServiceLock.readLock().lock();
                            if (mService == null) {
                                if (VDBG) Log.d(TAG, "Binding service...");
                                doBind();
                            }
                        } catch (Exception re) {
                            Log.e(TAG, "", re);
                        } finally {
                            mServiceLock.readLock().unlock();
                        }
                    }
                }
            };

    /**
     * Create a BluetoothHearingAid proxy object for interacting with the local
     * Bluetooth Hearing Aid service.
     */
    /*package*/ BluetoothHearingAid(Context context, ServiceListener l) {
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        }

        doBind();
    }

    boolean doBind() {
        Intent intent = new Intent(IBluetoothHearingAid.class.getName());
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, mConnection, 0,
                android.os.Process.myUserHandle())) {
            Log.e(TAG, "Could not bind to Bluetooth Hearing Aid Service with " + intent);
            return false;
        }
        return true;
    }

    /*package*/ void close() {
        mServiceListener = null;
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        }

        try {
            mServiceLock.writeLock().lock();
            if (mService != null) {
                mService = null;
                mContext.unbindService(mConnection);
            }
        } catch (Exception re) {
            Log.e(TAG, "", re);
        } finally {
            mServiceLock.writeLock().unlock();
        }
    }

    @Override
    public void finalize() {
        // The empty finalize needs to be kept or the
        // cts signature tests would fail.
    }

    /**
     * Initiate connection to a profile of the remote bluetooth device.
     *
     * <p> Currently, the system supports only 1 connection to the
     * Hearing Aid profile. The API will automatically disconnect connected
     * devices before connecting.
     *
     * <p> This API returns false in scenarios like the profile on the
     * device is already connected or Bluetooth is not turned on.
     * When this API returns true, it is guaranteed that
     * connection state intent for the profile will be broadcasted with
     * the state. Users can get the connection state of the profile
     * from this intent.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     * @hide
     */
    public boolean connect(BluetoothDevice device) {
        if (DBG) log("connect(" + device + ")");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled() && isValidDevice(device)) {
                return mService.connect(device);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return false;
        } finally {
            mServiceLock.readLock().unlock();
        }
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
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     * @hide
     */
    public boolean disconnect(BluetoothDevice device) {
        if (DBG) log("disconnect(" + device + ")");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled() && isValidDevice(device)) {
                return mService.disconnect(device);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return false;
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        if (VDBG) log("getConnectedDevices()");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled()) {
                return mService.getConnectedDevices();
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return new ArrayList<BluetoothDevice>();
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return new ArrayList<BluetoothDevice>();
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (VDBG) log("getDevicesMatchingStates()");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled()) {
                return mService.getDevicesMatchingConnectionStates(states);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return new ArrayList<BluetoothDevice>();
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return new ArrayList<BluetoothDevice>();
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getConnectionState(BluetoothDevice device) {
        if (VDBG) log("getState(" + device + ")");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled()
                    && isValidDevice(device)) {
                return mService.getConnectionState(device);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return BluetoothProfile.STATE_DISCONNECTED;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return BluetoothProfile.STATE_DISCONNECTED;
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * Set priority of the profile
     *
     * <p> The device should already be paired.
     * Priority can be one of {@link #PRIORITY_ON} orgetBluetoothManager
     * {@link #PRIORITY_OFF},
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission.
     *
     * @param device Paired bluetooth device
     * @param priority
     * @return true if priority is set, false on error
     * @hide
     */
    public boolean setPriority(BluetoothDevice device, int priority) {
        if (DBG) log("setPriority(" + device + ", " + priority + ")");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled()
                    && isValidDevice(device)) {
                if (priority != BluetoothProfile.PRIORITY_OFF
                        && priority != BluetoothProfile.PRIORITY_ON) {
                    return false;
                }
                return mService.setPriority(device, priority);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return false;
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * Get the priority of the profile.
     *
     * <p> The priority can be any of:
     * {@link #PRIORITY_AUTO_CONNECT}, {@link #PRIORITY_OFF},
     * {@link #PRIORITY_ON}, {@link #PRIORITY_UNDEFINED}
     *
     * @param device Bluetooth device
     * @return priority of the device
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public int getPriority(BluetoothDevice device) {
        if (VDBG) log("getPriority(" + device + ")");
        try {
            mServiceLock.readLock().lock();
            if (mService != null && isEnabled()
                    && isValidDevice(device)) {
                return mService.getPriority(device);
            }
            if (mService == null) Log.w(TAG, "Proxy not attached to service");
            return BluetoothProfile.PRIORITY_OFF;
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
            return BluetoothProfile.PRIORITY_OFF;
        } finally {
            mServiceLock.readLock().unlock();
        }
    }

    /**
     * Helper for converting a state to a string.
     *
     * For debug use only - strings are not internationalized.
     *
     * @hide
     */
    public static String stateToString(int state) {
        switch (state) {
            case STATE_DISCONNECTED:
                return "disconnected";
            case STATE_CONNECTING:
                return "connecting";
            case STATE_CONNECTED:
                return "connected";
            case STATE_DISCONNECTING:
                return "disconnecting";
            case STATE_PLAYING:
                return "playing";
            case STATE_NOT_PLAYING:
                return "not playing";
            default:
                return "<unknown state " + state + ">";
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "Proxy object connected");
            try {
                mServiceLock.writeLock().lock();
                mService = IBluetoothHearingAid.Stub.asInterface(Binder.allowBlocking(service));
            } finally {
                mServiceLock.writeLock().unlock();
            }

            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothProfile.HEARING_AID,
                                                    BluetoothHearingAid.this);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.d(TAG, "Proxy object disconnected");
            try {
                mServiceLock.writeLock().lock();
                mService = null;
            } finally {
                mServiceLock.writeLock().unlock();
            }
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(BluetoothProfile.HEARING_AID);
            }
        }
    };

    private boolean isEnabled() {
        if (mAdapter.getState() == BluetoothAdapter.STATE_ON) return true;
        return false;
    }

    private boolean isValidDevice(BluetoothDevice device) {
        if (device == null) return false;

        if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) return true;
        return false;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
