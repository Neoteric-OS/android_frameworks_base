package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface IScanListener {
    // notify events during scan.
    void onEvent(in Bundle eventArgs);
    // notify the scan progress.
    void onScanProgress(in String scanProgress, in Bundle scanProgressInfo);
    // notify the scan completion.
    void onScanCompleted(in int scanResult);
    // notify that the temporaily held channel list is stored.
    void onStoreCompleted(in int storeResult);
}