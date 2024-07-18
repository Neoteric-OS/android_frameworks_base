package android.media.tv.scan;

import android.media.tv.scan.ILcnConflictListener;

import android.os.Bundle;


/**
 * Country: Italy, France
 * Broadcast Type: BROADCAST_TYPE_DVB_T
 *
 * @hide
 */
interface ILcnConflict {
    // Get the LCN conflict groups information, If there are no conflicts, the array of Bundle is empty.
    Bundle[] getLcnConflictGroups();
    // Resolve LCN conflicts caused by service scans.
    int resolveLcnConflict(in Bundle[] lcnConflictSettings);
    // Set the listener to be invoked the LCN conflict event.
    int setListener(in ILcnConflictListener listener);
}