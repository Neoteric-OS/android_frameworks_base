/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.net.ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

/** Class that includes connectivity information for a specific Network at a specific time. */
public final class ConnectivityReport implements Parcelable {
    /** The Network for which this ConnectivityReport applied */
    @NonNull public final Network network;

    /**
     * The timestamp for the report. The timestamp is taken from {@link System#currentTimeMillis}.
     */
    public final long reportTimestamp;

    /** LinkProperties available on the Network at the reported timestamp */
    @NonNull public final LinkProperties linkProperties;

    /** NetworkCapabilities available on the Network at the reported timestamp */
    @NonNull public final NetworkCapabilities networkCapabilities;

    /** PersistableBundle that may contain additional info about the report */
    @NonNull public final PersistableBundle additionalInfo;

    /**
     * Constructor for ConnectivityReport.
     *
     * <p>Apps should obtain instances through {@link
     * ConnectivityDiagnosticsCallback#onConnectivityReport} instead of instantiating their own
     * instances (unless for testing purposes).
     *
     * @param network The Network for which this ConnectivityReport applies
     * @param reportTimestamp The timestamp for the report
     * @param linkProperties The LinkProperties available on network at reportTimestamp
     * @param networkCapabilities The NetworkCapabilities available on network at reportTimestamp
     * @param additionalInfo A PersistableBundle that may contain additional info about the report
     */
    public ConnectivityReport(
            @NonNull Network network,
            long reportTimestamp,
            @NonNull LinkProperties linkProperties,
            @NonNull NetworkCapabilities networkCapabilities,
            @NonNull PersistableBundle additionalInfo) {
        this.network = network;
        this.reportTimestamp = reportTimestamp;
        this.linkProperties = linkProperties;
        this.networkCapabilities = networkCapabilities;
        this.additionalInfo = additionalInfo;
    }

    /** {@inheritDoc} */
    @Override
    public int describeContents() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        network.writeToParcel(dest, flags);
        dest.writeLong(reportTimestamp);
        linkProperties.writeToParcel(dest, flags);
        networkCapabilities.writeToParcel(dest, flags);
        additionalInfo.writeToParcel(dest, flags);
    }

    /** Implement the Parcelable interface */
    public static final @NonNull Creator<ConnectivityReport> CREATOR =
            new Creator<ConnectivityReport>() {
                public ConnectivityReport createFromParcel(Parcel in) {
                    return new ConnectivityReport(
                            Network.CREATOR.createFromParcel(in),
                            in.readLong(),
                            LinkProperties.CREATOR.createFromParcel(in),
                            NetworkCapabilities.CREATOR.createFromParcel(in),
                            PersistableBundle.CREATOR.createFromParcel(in));
                }

                public ConnectivityReport[] newArray(int size) {
                    return new ConnectivityReport[size];
                }
            };
}
