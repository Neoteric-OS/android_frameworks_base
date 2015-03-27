package android.bluetooth;

import android.bluetooth.IBluetoothVS;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

/**
 * System private API used to send and receive vendor specific HCI commands and events
 * from the bluetooth controller.
 * {@hide}
 */
public class BluetoothVS {

    private static final String TAG = "BluetoothVS";

    public interface BluetoothVSCallbacks {
        /** Interface has completed initialization and is ready to use. */
        void onInterfaceReady();
        /** Some error occured and this VS interface instance is no longer usable. */
        void onInterfaceDown();
        /** A command complete was received for a previously send VS command. */
        void onCommandCompleteReceived(short commandOpcode, byte[] returnParams);
        /** A VS event that matches the current event filter was received. */
        void onEventReceived(byte[] params);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized(mConnection) {
                mService = null;
            }
            release(false);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized(mConnection) {
                mService = IBluetoothVS.Stub.asInterface(service);
                try {
                    mService.registerVSCallback(mVSCallbackStub);
                } catch (RemoteException e) {
                    Log.e(TAG,"",e);
                    release(false);
                }
            }
        }
    };

    private final IBluetoothVSCallback mVSCallbackStub = new IBluetoothVSCallback.Stub() {
        @Override
        public void vendorSpecificCommandCompleteReceived(int opcode, byte[] parameters) {
            synchronized (mConnection) {
                if (!mActive || mReleased) {
                    return;
                }

                mCallbacks.onCommandCompleteReceived((short)opcode, parameters);
            }
        }

        @Override
        public void vendorSpecificEventReceived(byte[] params) {
            synchronized (mConnection) {
                // Need to deal with the fact that multiple onInterfaceReady calls may be received.
                if (!mActive || mReleased) {
                    return;
                }

                mCallbacks.onEventReceived(params);
            }
        }

        @Override
        public void onInterfaceReady() {
            synchronized (mConnection) {
                if (mActive || mReleased) {
                    return;
                }

                mActive = true;
                mCallbacks.onInterfaceReady();
            }
        }

        @Override
        public void onInterfaceDown() {
            release(false);
        }
    };

    private final BluetoothVSCallbacks mCallbacks;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context mContext;
    private boolean mActive = false;
    private boolean mReleased = false;
    private IBluetoothVS mService;

    public BluetoothVS(Context context, BluetoothVSCallbacks callbacks) {
        mCallbacks = callbacks;
        mContext = context;
        Intent intent = new Intent(IBluetoothVS.class.getName());
        if(!context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE))
        {
            release(false);
        }
    }

    /**
     * Send a Vendor Specific Command to the Chip.
     */
    public void sendVendorSpecificCommand(short opcode, byte [] parameters) {
        synchronized (mConnection) {
            if (mService == null) return;
            try {
                mService.sendVendorSpecificCommand(opcode, parameters);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
                release(false);
            }
        }
    }

    public void setVendorSpecificEventFilter(byte[] mask, byte[] value) {
        synchronized (mConnection) {
            if (mService == null) return;
            try {
                mService.setVSEventFilter(mVSCallbackStub, mask, value);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
                release(false);
            }
        }
    }

    public void clearVendorSpecificEventFilter() {
        synchronized (mConnection) {
            if (mService == null) return;
            try {
                mService.setVSEventFilter(mVSCallbackStub, null, null);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
                release (false);
            }
        }
    }

    private void release(boolean suppressEvent) {
        synchronized(mConnection) {
            if(mReleased) return;
            mReleased = true;

            if(mService != null) {
                try {
                    mService.unregisterVSCallback(mVSCallbackStub);
                } catch (RemoteException e) {Log.e(TAG,"",e);}
            }

            mContext.unbindService(mConnection);
            if(!suppressEvent)
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallbacks.onInterfaceDown();
                    }
                });
            }
        }
    }

    public void release() {
        release(true);
    }
}
