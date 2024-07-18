package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
interface IHDPlusInfo {
    // Specifying a HDPlusInfo and start a network scan.
    int setHDPlusInfo(in Bundle HDPlusInfo);
}