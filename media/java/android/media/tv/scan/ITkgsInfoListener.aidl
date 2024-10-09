package android.media.tv.scan;


/**
 * @hide
 */
oneway interface ITkgsInfoListener {
    void onServiceList(in String[] serviceList);
    void onTableVersionUpdate(int tableVersion);
    void onUserMessage(String strMessage);
}