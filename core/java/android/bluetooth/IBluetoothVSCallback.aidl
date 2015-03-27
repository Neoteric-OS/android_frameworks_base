package android.bluetooth;

/**
 * System private API for Bluetooth service vendor specific callbacks.
 * @hide
 */
interface IBluetoothVSCallback
{
    /**
     * Interface is now finished intialization and is ready to use.
     * Note that this may be called multiple times
     */
    void onInterfaceReady();

    /**
     * Some error occured and the VS interface is no longer usable.
     * This may happen withough {@link #onInterfaceReady()} ever being called.
     */
    void onInterfaceDown();

    /** A command complete was received for a previously sent VS command. */
    void vendorSpecificCommandCompleteReceived(int opcode, in byte [] parameters);

    /** A vendor specific event was received from the controller */
    void vendorSpecificEventReceived(in byte [] params);
}
