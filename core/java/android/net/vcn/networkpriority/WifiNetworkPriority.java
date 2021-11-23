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
import android.annotation.Nullable;

/** @hide */
public final class WifiNetworkPriority extends NetworkPriority {
    private final String mSsid;

    public WifiNetworkPriority(int networkQuality, boolean allowMetered, String ssid) {
        super(networkQuality, allowMetered);
        mSsid = ssid;
    }

    @Nullable
    public String getSsid() {
        return mSsid;
    }

    public static class Builder extends NetworkPriority.Builder<Builder, WifiNetworkPriority> {
        private String mSsid;

        public Builder() {}

        @NonNull
        public Builder setSsid(@Nullable String ssid) {
            mSsid = ssid;
            return this;
        }

        @Override
        @NonNull
        public WifiNetworkPriority build() {
            return new WifiNetworkPriority(mNetworkQuality, mAllowMetered, mSsid);
        }
    }
}
