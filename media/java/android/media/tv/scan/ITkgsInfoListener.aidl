package android.media.tv.scan;


import android.os.Bundle;


/**
 * @hide
 */
oneway interface ITkgsInfoListener {
    void onServiceList(in List<Bundle> serviceList);
    void onTableVersionUpdate(int tableVersion);
    void onUserMessage(String strMessage);
}