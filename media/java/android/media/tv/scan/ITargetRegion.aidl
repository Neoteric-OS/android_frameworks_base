package android.media.tv.scan;

import android.media.tv.scan.ITargetRegionListener;

import android.os.Bundle;


/**
 * Country: U.K.
 * Broadcast Type: BROADCAST_TYPE_DVB_T
 *
 * @hide
 */
interface ITargetRegion {
    // Get the target regions information. If there are no conflicts, the array of Bundle is empty.
    Bundle[] getTargetRegions();
    // Select and set one of two or more target region detected by the service scan.
    int setTargetRegion(in Bundle targetRegionSettings);
    // Set the listener to be invoked when two or more regions are detected.
    int setListener(in ITargetRegionListener listener);
}