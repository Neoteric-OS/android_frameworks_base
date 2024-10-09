package android.media.tv.scan;

import android.os.Bundle;


/**
 * @hide
 */
oneway interface IFavoriteNetworkListener {
    void onDetectFavoriteNetwork(in Bundle detectFavoriteNetworks);
}