/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Base class for network providers such as telephony or Wi-Fi. NetworkProviders connect the device
 * to networks and makes them available to the core network stack by creating
 * {@link NetworkAgent}s. The networks can then provide connectivity to apps and can be interacted
 * with via networking APIs such as {@link ConnectivityManager}.
 *
 * Subclasses should implement {@link #onNetworkRequested} and {@link #onNetworkRequestWithdrawn}
 * to receive {@link NetworkRequest}s sent by the system and by apps. A network that is not the
 * best (highest-scoring) network for any request is generally not used by the system, and torn
 * down.
 *
 * @hide
 */
@SystemApi
public class NetworkProvider {
    /**
     * {@code providerId} value that indicates the absence of a provider. It is the providerId of
     * any NetworkProvider that is not currently registered, and of any NetworkRequest that is not
     * currently being satisfied by a network.
     */
    public static final int ID_NONE = -1;

    /**
     * The first providerId value that will be allocated.
     * @hide only used by ConnectivityService.
     */
    public static final int FIRST_PROVIDER_ID = 1;

    private final Messenger mMessenger; // TODO : this is now unused. Remove.
    private final String mName;
    private final ConnectivityManager mCm;

    private int mProviderId = ID_NONE;

    /**
     * Constructs a new NetworkProvider.
     *
     * @param looper the Looper on which to run {@link #onNetworkRequested} and
     *               {@link #onNetworkRequestWithdrawn}.
     * @param name the name of the listener, used only for debugging.
     *
     * @hide
     */
    @SystemApi
    public NetworkProvider(@NonNull Context context, @NonNull Looper looper, @NonNull String name) {
        mCm = ConnectivityManager.from(context);

        Handler handler = new Handler(looper);
        mMessenger = new Messenger(handler);
        mName = name;
    }

    // TODO: consider adding a register() method so ConnectivityManager does not need to call this.
    /** @hide */
    public @Nullable Messenger getMessenger() {
        return mMessenger;
    }

    /** @hide */
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Returns the ID of this provider. This is known only once the provider is registered via
     * {@link ConnectivityManager#registerNetworkProvider()}, otherwise the ID is {@link #ID_NONE}.
     * This ID must be used when registering any {@link NetworkAgent}s.
     */
    public int getProviderId() {
        return mProviderId;
    }

    /** @hide */
    public void setProviderId(int providerId) {
        mProviderId = providerId;
    }

    /**
     *  Called when a NetworkRequest is received. The request may be a new request or an existing
     *  request with a different score.
     *
     * @param request the NetworkRequest being received
     * @param score the score of the network currently satisfying the request, or 0 if none.
     * @param providerId the ID of the provider that created the network currently satisfying this
     *                   request, or {@link #ID_NONE} if none.
     *
     *  @hide
     */
    @SystemApi
    public void onNetworkRequested(@NonNull NetworkRequest request,
            @IntRange(from = 0, to = 99) int score, int providerId) {}

    /**
     *  Called when a NetworkRequest is withdrawn.
     *  @hide
     */
    @SystemApi
    public void onNetworkRequestWithdrawn(@NonNull NetworkRequest request) {}

    /**
     * Asserts that no provider will ever be able to satisfy the specified request. The provider
     * must only call this method if it knows that it is the only provider on the system capable of
     * satisfying this request, and that the request cannot be satisfied. The application filing the
     * request will receive an {@link NetworkCallback#onUnavailable()} callback.
     *
     * @param request the request that permanently cannot be fulfilled
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.NETWORK_FACTORY)
    public void declareNetworkRequestUnfulfillable(@NonNull NetworkRequest request) {
        mCm.declareNetworkRequestUnfulfillable(request);
    }

    /** @hide */
    @SystemApi
    public interface NeedNetwork {
        void onOfferNeeded(final @NonNull NetworkRequest request, final int factorySerialNumber);
        void onOfferUnneeded(final @NonNull NetworkRequest request);
    }

    private class NeedNetworkProxy extends INeedNetwork.Stub {
        @NonNull public final NeedNetwork callback;
        @NonNull private final Executor mExecutor;

        public NeedNetworkProxy(@NonNull final NeedNetwork callback,
                @NonNull final Executor executor) {
            this.callback = callback;
            this.mExecutor = executor;
        }

        @Override
        public void onOfferNeeded(final @NonNull NetworkRequest request,
                final int factorySerialNumber) {
            mExecutor.execute(() -> callback.onOfferNeeded(request, factorySerialNumber));
        }

        @Override
        public void onOfferUnneeded(final @NonNull NetworkRequest request) {
            mExecutor.execute(() -> callback.onOfferUnneeded(request));
        }
    }

    @GuardedBy("mProxies")
    @NonNull private final ArrayList<NeedNetworkProxy> mProxies = new ArrayList<>();

    /** @hide */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.NETWORK_FACTORY)
    public void offerNetwork(@NonNull final NetworkScore score,
            @NonNull final NetworkCapabilities caps, @NonNull final Executor executor,
            @NonNull final NeedNetwork callback) {
        final NeedNetworkProxy proxy = new NeedNetworkProxy(callback, executor);
        synchronized (mProxies) {
            mProxies.add(proxy);
        }
        mCm.offerNetwork(this, score, caps, proxy);
    }

    /** @hide */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.NETWORK_FACTORY)
    public void unofferNetwork(final @NonNull NeedNetwork callback) {
        NeedNetworkProxy proxy = null;
        synchronized (mProxies) {
            for (final NeedNetworkProxy p : mProxies) {
                if (p.callback == callback) {
                    proxy = p;
                    break;
                }
            }
            if (null == proxy) return;
            mProxies.remove(proxy);
        }
        mCm.unofferNetwork(proxy);
    }
}
