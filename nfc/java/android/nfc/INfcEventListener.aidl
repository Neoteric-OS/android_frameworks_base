package android.nfc;

import android.nfc.ComponentNameAndUser;

/**
 * @hide
 */
oneway interface INfcEventListener {
    void onPreferredServiceChanged(in ComponentNameAndUser ComponentNameAndUser);
    void onObserveModeStateChanged(boolean isEnabled);
    void onAidConflictOccurred(in String aid);
    void onAidNotRouted(in List<String> aidList);
    void onNfcStateChanged(in int nfcState);
    void onRemoteFieldChanged(boolean isActivated);
    void onInternalErrorReported(in int errorType);
}