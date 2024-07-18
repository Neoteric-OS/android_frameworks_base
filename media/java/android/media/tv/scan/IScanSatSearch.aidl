package android.media.tv.scan;

import android.os.Bundle;


/**
 * For satellite search function.
 * @hide
 */
interface IScanSatSearch {
    // Set currecnt LNB as customized LNB, default LNB is universal LNB
    int setCustomizedLnb(in Bundle customizedLnb);
}