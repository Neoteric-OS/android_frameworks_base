package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface IScanListener {
    // notify events during scan.
    void onEvent(in Bundle eventArgs);
    // notify the scan progress.
    void onScanProgress(in Bundle progressInfo);
    // notify the scan completion.
    void onScanCompleted(in Bundle scanResult);
    // notify that the temporaily held channel list is stored.
    void onStoreCompleted(in Bundle storeResult);
}