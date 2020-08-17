package com.android.server.connectivity;

import android.annotation.NonNull;
import android.net.INeedNetwork;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.os.Messenger;

public class NetworkOffer {
    @NonNull public final NetworkScore score;
    @NonNull public final NetworkCapabilities caps;
    @NonNull public final INeedNetwork callback;
    @NonNull public final Messenger provider;

    public NetworkOffer(@NonNull final NetworkScore score, @NonNull final NetworkCapabilities caps,
            @NonNull final INeedNetwork callback, @NonNull final Messenger provider) {
        this.score = score;
        this.caps = caps;
        this.callback = callback;
        this.provider = provider;
    }

    // Can this network satisfy this request ?
    public boolean canSatisfy(@NonNull final NetworkRequest request) {
        return request.networkCapabilities.satisfiedByNetworkCapabilities(caps);
    }
}
