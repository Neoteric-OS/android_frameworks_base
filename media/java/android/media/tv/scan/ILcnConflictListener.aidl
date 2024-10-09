package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface ILcnConflictListener {
    void onDetectLcnConflict(in Bundle detectLcnConflicts);
}