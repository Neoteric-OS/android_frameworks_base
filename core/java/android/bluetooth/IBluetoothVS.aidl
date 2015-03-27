package android.bluetooth;

import android.bluetooth.IBluetoothStateChangeCallback;
import android.bluetooth.IBluetoothVSCallback;

/**
 * System private API for sending vendor specific commands and receiving
 * vendor specific events from the bluetooth controller.
 * {@hide}
 */
interface IBluetoothVS {
    /** Registers a set of callbacks to use with the interface. */
    void registerVSCallback(in IBluetoothVSCallback callback);

    /** Unregisters a set of callbacks that were previously registered. */
    void unregisterVSCallback(in IBluetoothVSCallback callback);

    /** Send a VS command to the controller. */
    void sendVendorSpecificCommand(int opcode, in byte [] parameters);

    /** Set up a filter describing which VS events a particular callback is interested in. */
    void setVSEventFilter(in IBluetoothVSCallback callback,
        in byte [] mask, in byte [] value);
}
