/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

/**
 * The Android Bluetooth PBAP Client Equipment(PCE).
 * Public API for controlling the Bluetooth Pbap client. This includes
 * Bluetooth Phone book Access profile PCE implementation.
 * BluetoothPce is a proxy object for controlling the BluetoothPce
 * via IPC.
 *
 * Creating a BluetoothPce object will create a binding with the
 * BluetoothPce service. Users of this object should call close() when they
 * are finished with the BluetoothPce, so that this proxy object can unbind
 * from the service.
 *
 * This BluetoothPce object is not immediately bound to the
 * Bluetooth Pce service. Use the ServiceListener interface to obtain a
 * notification when it is bound, this is especially important if you wish to
 * immediately call methods on BluetoothPce after construction.
 *
 * Android only supports one connected Bluetooth Pse(PBAP Server Equipment) at a time.
 *
 */
public class BluetoothPce {

    private static final String TAG = "BluetoothPce";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private IBluetoothPce mService;
    private final Context mContext;
    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;

    /** There was an error trying to obtain the state */
    public static final int STATE_ERROR        = -1;
    /** No connected Pse */
    public static final int STATE_DISCONNECTED = 0;
    /** Connection attempt in progress */
    public static final int STATE_CONNECTING   = 1;
    /** Pse is currently connected */
    public static final int STATE_CONNECTED    = 2;

    /**
     * An interface for notifying Bluetooth PCE IPC clients when they have
     * been connected to the BluetoothPce service.
     */
    public interface ServiceListener {
        /**
         * Called to notify the client when this proxy object has been
         * connected to the BluetoothPce service. Clients must wait for
         * this callback before making IPC calls on the BluetoothPce
         * service.
         */
        public void onServiceConnected(BluetoothPce proxy);

        /**
         * Called to notify the client that this proxy object has been
         * disconnected from the BluetoothPce service. Clients must not
         * make IPC calls on the BluetoothPce service after this callback.
         */
        public void onServiceDisconnected();
    }

    /**
     * Listen and react to bluetooth state changes
     */
    final private IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
            new IBluetoothStateChangeCallback.Stub() {
                public void onBluetoothStateChange(boolean up) {
                    if (DBG) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                    if (!up) {
                        if (VDBG) Log.d(TAG,"Unbinding service...");
                        synchronized (mConnection) {
                            try {
                                mService = null;
                                mContext.unbindService(mConnection);
                            } catch (Exception re) {
                                Log.e(TAG,"",re);
                            }
                        }
                    } else {
                        synchronized (mConnection) {
                            try {
                                if (mService == null) {
                                    if (VDBG) Log.d(TAG,"Binding service...");
                                    doBind();
                                }
                            } catch (Exception re) {
                                Log.e(TAG,"",re);
                            }
                        }
                    }
                }
        };

    /**
     * Create a BluetoothPce proxy object.
     */
    public BluetoothPce(Context context, ServiceListener l) {
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }
        doBind();
    }

    /**
     * Bind to bluetooth pce service
     */
    boolean doBind() {
        Intent intent = new Intent(IBluetoothPce.class.getName());
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindService(intent, mConnection, 0)) {
            Log.e(TAG, "Could not bind to Bluetooth Pce Service with " + intent);
            return false;
        }
        return true;
    }

    /**
     * Close the connection to the backing service.
     * Other public functions of BluetoothPce will return default error
     * results once close() has been called. Multiple invocations of close()
     * are ok.
     */
    public synchronized void close() {
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (Exception e) {
                Log.e(TAG,"",e);
            }
        }

        synchronized (mConnection) {
            if (mService != null) {
                try {
                    mService = null;
                    mContext.unbindService(mConnection);
                } catch (Exception re) {
                    Log.e(TAG,"",re);
                }
            }
        }
        mServiceListener = null;
    }

    /**
     * Get the current state of the BluetoothPce service.
     * @return One of the STATE_X return codes, or STATE_ERROR if this proxy
     *         object is currently not connected to the PCE service.
     */
    public int getState() {
        if (VDBG) log("getState()");
        if (mService != null) {
            try {
                return mService.getState();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return BluetoothPce.STATE_ERROR;
    }

    /**
     * Get the currently connected remote Bluetooth device (PSE).
     * @return The remote Bluetooth device, or null if not in connected or
     *         connecting state, or if this proxy object is not connected to
     *         the PCE service.
     */
    public BluetoothDevice getPse() {
        if (VDBG) log("getClient()");
        if (mService != null) {
            try {
                return mService.getPse();
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return null;
    }

    /**
     * Returns true if the specified Bluetooth device is connected (does not
     * include connecting). Returns false if not connected, or if this proxy
     * object is not currently connected to the Pce service.
     */
    public boolean isConnected(BluetoothDevice device) {
        if (VDBG) log("isConnected(" + device + ")");
        if (mService != null) {
            try {
                return mService.isConnected(device);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return false;
    }
    
    /**
     * Returns true if successfully connected to the specified Bluetooth device 
     * Returns false if connecte fail.
     */   
    public boolean connect (BluetoothDevice device){
        if (VDBG) log("Connect to (" + device + ")");
        if (mService != null) {
            try {
                return mService.connect(device);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return false;
    	
    }

    /**
     * Disconnects the current PCE. Currently this call blocks,
     * it may soon be made asynchronous. Returns false if this proxy object is
     * not currently connected to the PCE service.
     */
    public boolean disconnect() {
        if (DBG) log("disconnect()");
        if (mService != null) {
            try {
                mService.disconnect();
                return true;
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return false;
    }
    
    /**
     * Pull phonebook
     * 
     * @param nameOfPath
     *     absolute path in the virtual folders architecture of the
     *     PSE.ex: telecom/pb.vcf,SIM1/telecom/pb.vcf.
     * @param propSelector
     *     indicate the properties contained in the requested vCard
     *     objects.
     * Returns vcard string if successfully get phonebook from PSE
     */
    public String pullPhonebook(String nameOfPath, int propSelector){
        if (DBG) log("pullPhonebook()");
        if (mService != null) {
            try {
                return mService.pullPhonebook(nameOfPath,propSelector);
            } catch (RemoteException e) {Log.e(TAG, e.toString());}
        } else {
            Log.w(TAG, "Proxy not attached to service");
            if (DBG) log(Log.getStackTraceString(new Throwable()));
        }
        return null;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) log("BluetoothPce Proxy object connected");
            mService = IBluetoothPce.Stub.asInterface(service);
            if (mServiceListener != null) {           	
                mServiceListener.onServiceConnected(BluetoothPce.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) log("BluetoothPce Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected();
            }
        }
    };

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
