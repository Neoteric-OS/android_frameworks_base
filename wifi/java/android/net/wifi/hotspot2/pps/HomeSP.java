/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package android.net.wifi.hotspot2.pps;

import android.os.Bundle;
import android.os.Parcelable;
import android.os.Parcel;

/**
 * Class representing HomeSP subtree in PerProviderSubscription (PPS)
 * Management Object (MO) tree.
 * For more info, refer to Hotspot 2.0 PPS MO defined in section 9.1 of the Hotspot 2.0
 * Release 2 Technical Specification.
 * 
 * @hide
 */
public final class HomeSP implements Parcelable {
    /**
     * FQDN (Fully Qualified Domain Name) of this home service provider.
     */
    public String fqdn;

    /**
     * Friendly name of this home service provider.
     */
    public String friendlyName;

    /**
     * Icon URL of this home service provider.
     */
    public String iconUrl;

    /**
     * IDs <SSID, HESSID> duple of the networks that are consider home networks.
     */
    public Bundle homeNetworkIds;

    /**
     * List of Organization Identifiers (OIs) identifying the service providers of which
     * this provider is a member.  Each entry contains duple of a HomeOI and a boolean
     * flag HomeOIRequired.  Refer to the Hotspot 2.0 Release 2 Technical Specification
     * for detail matching criteria.
     */
    public Bundle homeOIList;

    /**
     * List of FQDN (Fully Qualified Domain Name) of partner providers.
     * These providers should also be regarded as home Hotspot operators.
     * This relationship is most likely achieved via a commercial agreement or
     * operator merges between the providers.
     */
    public String[] otherHomePartners;

    /**
     * List of Organization Identifiers (OIs) identifying a roaming consortium of
     * which this provider is a member.
     */
    public long[] roamingConsortiumOIs;

    public HomeSP() {
        fqdn = null;
        friendlyName = null;
        iconUrl = null;
        homeNetworkIds = null;
        homeOIList = null;
        otherHomePartners = null;
        roamingConsortiumOIs = null;
    }

    /** Implement the Parcelable interface {@hide} */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fqdn);
        dest.writeString(friendlyName);
        dest.writeString(iconUrl);
        dest.writeBundle(homeNetworkIds);
        dest.writeBundle(homeOIList);
        dest.writeStringArray(otherHomePartners);
        dest.writeLongArray(roamingConsortiumOIs);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<HomeSP> CREATOR =
        new Creator<HomeSP>() {
            @Override
            public HomeSP createFromParcel(Parcel in) {
                HomeSP homeSp = new HomeSP();
                homeSp.fqdn = in.readString();
                homeSp.friendlyName = in.readString();
                homeSp.iconUrl = in.readString();
                homeSp.homeNetworkIds = in.readBundle();
                homeSp.homeOIList = in.readBundle();
                homeSp.otherHomePartners = in.createStringArray();
                homeSp.roamingConsortiumOIs = in.createLongArray();
                return homeSp;
            }

            @Override
            public HomeSP[] newArray(int size) {
                return new HomeSP[size];
            }
        };
}

