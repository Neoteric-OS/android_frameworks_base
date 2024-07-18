package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface ILcnV2ChannelListListener {
    void onDetectLcnV2ChannelList(in Bundle detectLcnV2ChannelList);
}