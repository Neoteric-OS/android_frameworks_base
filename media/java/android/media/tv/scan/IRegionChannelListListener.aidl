package android.media.tv.scan;


/**
 * @hide
 */
oneway interface IRegionChannelListListener {
    void onDetectRegionChannelList(in String[] detectRegionChannelList);
}