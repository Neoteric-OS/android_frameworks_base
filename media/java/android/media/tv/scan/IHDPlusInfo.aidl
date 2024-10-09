package android.media.tv.scan;


/**
 * @hide
 */
interface IHDPlusInfo {
    // Specifying a HDPlusInfo and start a network scan.
    int setHDPlusInfo(in String isBlindScanContinue, in String isHDMode);
}