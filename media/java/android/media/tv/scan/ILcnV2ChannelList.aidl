package android.media.tv.scan;

import android.media.tv.scan.ILcnV2ChannelListListener;

import android.os.Bundle;


/**
 * Country: (NorDig etc.)
 * Broadcast Type: BROADCAST_TYPE_DVB_T, BROADCAST_TYPE_DVB_C
 *
 * @hide
 */
interface ILcnV2ChannelList {
    // Get the LCN V2 channel list information. If there are no conflicts, the array of Bundle is empty.
    Bundle[] getLcnV2ChannelLists();
    // Select and set one of two or more LCN V2 channel list detected by the service scan.
    int setLcnV2ChannelList(in Bundle lcnV2ChannelListSettings);
    // Set the listener to be invoked when two or more LCN V2 channel list are detected.
    int setListener(in ILcnV2ChannelListListener listener);
}