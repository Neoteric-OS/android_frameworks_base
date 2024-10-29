package android.nfc;

import android.content.ComponentName;

/**
 * @hide
 */
oneway interface INfcEventListener {
    void onPreferredServiceChanged(in ComponentName componentName, int userId);
    void onObserveModeStateChanged(boolean isEnabled);
}