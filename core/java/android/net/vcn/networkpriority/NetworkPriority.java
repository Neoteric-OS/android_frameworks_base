/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.net.vcn.networkpriority;

import android.annotation.NonNull;

/** @hide */
public abstract class NetworkPriority {
    public static final int NETWORK_QUALITY_OK = 0;
    public static final int NETWORK_QUALITY_ANY = 1;

    private final int mNetworkQuality;
    private final boolean mAllowMetered;

    protected NetworkPriority(int networkQuality, boolean allowMetered) {
        mNetworkQuality = networkQuality;
        mAllowMetered = allowMetered;
    }

    public int getNetworkQuality() {
        return mNetworkQuality;
    }

    public boolean allowMetered() {
        return mAllowMetered;
    }

    public abstract static class Builder<T extends Builder, E extends NetworkPriority> {
        protected int mNetworkQuality;
        protected boolean mAllowMetered;

        protected Builder() {}

        @NonNull
        public Builder<T, E> setNetworkQuality(int networkQuality) {
            mNetworkQuality = networkQuality;
            return this;
        }

        @NonNull
        public Builder<T, E> setAllowMetered(boolean allowMetered) {
            mAllowMetered = allowMetered;
            return this;
        }

        @NonNull
        abstract E build();
    }
}
