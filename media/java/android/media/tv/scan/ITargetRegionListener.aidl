package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface ITargetRegionListener {
    void onDetectTargetRegion(in Bundle detectTargetRegions);
}