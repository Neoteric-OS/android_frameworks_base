package android.media.tv.scan;

import android.media.tv.scan.IFavoriteNetworkListener;

import android.os.Bundle;


/**
 * Country: Norway
 * Broadcast Type: BROADCAST_TYPE_DVB_T
 * (Operator: RiksTV)
 *
 * @hide
 */
interface IFavoriteNetwork {
    // Get the favorite network information,If there are no conflicts, the array of Bundle is empty.
    Bundle[] getFavoriteNetworks();
    // Select and set one of two or more favorite networks detected by the service scan.
    int setFavoriteNetwork(in Bundle favoriteNetworkSettings);
    // Set the listener to be invoked when two or more favorite networks are detected.
    int setListener(in IFavoriteNetworkListener listener);
}