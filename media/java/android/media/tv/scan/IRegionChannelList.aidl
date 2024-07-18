package android.media.tv.scan;

import android.media.tv.scan.IRegionChannelListListener;

import android.os.Bundle;


/**
 * @hide
 */
interface IRegionChannelList {
    // Set the region channel list for scanning.
    int setRegionChannelList(in String regionChannelList);
    // Set the listener to be invoked when one or more region channel list has been detected by region channel list searches.
    int setListener(in IRegionChannelListListener listener);
}