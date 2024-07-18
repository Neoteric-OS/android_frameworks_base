package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface IRegionChannelListListener {
    void onDetectRegionChannelList(in Bundle detectRegionChannelList);
}