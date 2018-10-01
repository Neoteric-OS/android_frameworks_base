/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.net.InetAddress;
import java.util.Arrays;

/** @hide */
@SystemApi
public class PrivateDnsConfig implements Parcelable {
    public final boolean useTls;
    public final String hostname;
    public final InetAddress[] ips;

    public PrivateDnsConfig() {
        this(false);
    }

    public PrivateDnsConfig(boolean useTls) {
        this.useTls = useTls;
        this.hostname = "";
        this.ips = new InetAddress[0];
    }

    public PrivateDnsConfig(String hostname, InetAddress[] ips) {
        this.useTls = !TextUtils.isEmpty(hostname);
        this.hostname = useTls ? hostname : "";
        this.ips = (ips != null) ? ips : new InetAddress[0];
    }

    public PrivateDnsConfig(PrivateDnsConfig cfg) {
        useTls = cfg.useTls;
        hostname = cfg.hostname;
        ips = cfg.ips;
    }

    protected PrivateDnsConfig(Parcel in) {
        useTls = in.readByte() != 0;
        hostname = in.readStringNoHelper();
        ips = (InetAddress[]) in.readSerializable();
    }

    public static final Creator<PrivateDnsConfig> CREATOR = new Creator<PrivateDnsConfig>() {
        @Override
        public PrivateDnsConfig createFromParcel(Parcel in) {
            return new PrivateDnsConfig(in);
        }

        @Override
        public PrivateDnsConfig[] newArray(int size) {
            return new PrivateDnsConfig[size];
        }
    };

    public boolean inStrictMode() {
        return useTls && !TextUtils.isEmpty(hostname);
    }

    public String toString() {
        return PrivateDnsConfig.class.getSimpleName() +
                "{" + useTls + ":" + hostname + "/" + Arrays.toString(ips) + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (useTls ? 1 : 0));
        dest.writeStringNoHelper(hostname);
        dest.writeSerializable(ips);
    }
}
