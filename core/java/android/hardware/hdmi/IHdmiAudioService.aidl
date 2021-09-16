package android.hardware.hdmi;

/**
 * Binder interface that clients running in the application process
 * will use to perform HDMI-CEC features by communicating with other devices
 * on the bus.
 *
 * @hide
 */
interface IHdmiAudioService {
    int getEarcStatus();
    String setEarcRawCaps(in byte[] rawCaps);
}