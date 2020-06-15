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

package android.telephony;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.net.LinkAddress;
import android.net.QosSession;
import android.net.QosSessionAttributes;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * Provides Qos attributes of an eps bearer.
 *
 * {@hide}
 */
@SystemApi
public final class EpsBearerQosSessionAttributes implements QosSessionAttributes, Parcelable {
    private final int mQci;
    private final long mMaxUplinkBitRate, mMaxDownlinkBitRate;
    private final long mGuaranteedUplinkBitRate, mGuaranteedDownlinkBitRate;
    @NonNull private final List<Pair<LinkAddress, Integer>> mRemoteAddresses;

    /**
     * The type of session that these attributes apply to
     *
     * @return the session type
     */
    @Override
    public int getSessionType() {
        return QosSession.TYPE_EPS_BEARER;
    }

    /**
     * The quality class indicator of the session
     *
     * @return the qci of the session
     */
    public int getQci() {
        return mQci;
    }

    /**
     * The guaranteed uplink bit rate in kbps for the overall Qos Session.
     *
     * Note: The Qos Session may be shared with OTHER applications besides yours.
     *
     * @return the guaranteed bit rate of the uplink
     */
    public long getGuaranteedUplinkBitRate() {
        return mGuaranteedUplinkBitRate;
    }

    /**
     * The guaranteed downlink bitrate in kbps.
     *
     * Note: The Qos Session may be shared with OTHER applications besides yours.
     *
     * @return the guaranteed bit rate of the downlink
     */
    public long getGuaranteedDownlinkBitRate() {
        return mGuaranteedDownlinkBitRate;
    }

    /**
     * The maximum allowed uplink bitrate in kbps.
     *
     * Note: The Qos Session may be shared with OTHER applications besides yours.
     *
     * @return the max uplink bit rate
     */
    public long getMaxUplinkBitRate() {
        return mMaxUplinkBitRate;
    }

    /**
     * The maximum allowed downlink bitrate in kbps.
     *
     * Note: The Qos Session may be shared with OTHER applications besides yours.
     *
     * @return the max downlink bit rate
     */
    public long getMaxDownlinkBitRate() {
        return mMaxDownlinkBitRate;
    }

    /**
     * List of remote addresses associated with the Qos Session.  The given uplink bit rates apply
     * to this given list of remote addresses.
     *
     * Note: In the event that the list is empty, it is assumed that the uplink bit rates apply to
     * all remote addresses that are not contained in a different set of attributes.
     *
     * Note: This is an unmodifiable list.
     *
     * @return a pair of addresses and ports
     */
    @NonNull
    public List<Pair<LinkAddress, Integer>> getRemoteAddresses() {
        return  mRemoteAddresses;
    }

    /**
     * ..ctor for attributes
     *
     * @param qci quality class indicator
     * @param maxUplinkBitRate the max uplink bit rate in kbps
     * @param maxDownlinkBitRate the max downlink bit rate in kbps
     * @param guaranteedDownlinkBitRate the guaranteed downlink bit rate in kbps
     * @param guaranteedUplinkBitRate the guaranteed uplink bit rate in kbps
     * @param remoteAddresses the remote addresses that the uplink bit rates apply to
     */
    public EpsBearerQosSessionAttributes(int qci,
            final long maxUplinkBitRate, final long maxDownlinkBitRate,
            final long guaranteedDownlinkBitRate, final long guaranteedUplinkBitRate,
            @NonNull final List<Pair<LinkAddress, Integer>> remoteAddresses) {
        mQci = qci;
        mMaxUplinkBitRate = maxUplinkBitRate;
        mMaxDownlinkBitRate = maxDownlinkBitRate;
        mGuaranteedUplinkBitRate = guaranteedUplinkBitRate;
        mGuaranteedDownlinkBitRate = guaranteedDownlinkBitRate;
        mRemoteAddresses = Collections.unmodifiableList(remoteAddresses);
    }

    EpsBearerQosSessionAttributes(@NonNull final Parcel in) {
        mQci = in.readInt();
        mMaxUplinkBitRate = in.readLong();
        mMaxDownlinkBitRate = in.readLong();
        mGuaranteedUplinkBitRate = in.readLong();
        mGuaranteedDownlinkBitRate = in.readLong();

        // Unparceling a list of pairs was not straight forward and so doing it manually.
        int size = in.readInt();
        List<Pair<LinkAddress, Integer>> remoteAddresses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            LinkAddress linkAddress = LinkAddress.CREATOR.createFromParcel(in);
            int port = in.readInt();
            remoteAddresses.add(new Pair<>(linkAddress, port));
        }
        mRemoteAddresses = Collections.unmodifiableList(remoteAddresses);
    }

    /**
     * Creates attributes based off of a parcel
     * @param in the parcel
     * @return the attributes
     */
    @NonNull
    public static EpsBearerQosSessionAttributes create(@NonNull final Parcel in) {
        return new EpsBearerQosSessionAttributes(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, int flags) {
        dest.writeInt(mQci);
        dest.writeLong(mMaxUplinkBitRate);
        dest.writeLong(mMaxDownlinkBitRate);
        dest.writeLong(mGuaranteedUplinkBitRate);
        dest.writeLong(mGuaranteedDownlinkBitRate);

        int size = mRemoteAddresses.size();
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            Pair<LinkAddress, Integer> pair = mRemoteAddresses.get(i);
            pair.first.writeToParcel(dest, flags);
            dest.writeInt(pair.second);
        }
    }

    @NonNull
    public static final Creator<EpsBearerQosSessionAttributes> CREATOR =
            new Creator<EpsBearerQosSessionAttributes>() {
        @NonNull
        @Override
        public EpsBearerQosSessionAttributes createFromParcel(@NonNull final Parcel in) {
            return new EpsBearerQosSessionAttributes(in);
        }

        @NonNull
        @Override
        public EpsBearerQosSessionAttributes[] newArray(int size) {
            return new EpsBearerQosSessionAttributes[size];
        }
    };
}
