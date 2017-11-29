/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony.ims.internal.stub;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.ims.internal.feature.ImsFeature;

/**
 * Container class for IMS registration configuration. This class contains three data types:
 * 1) features that the ImsService supports, which are defined in {@link ImsFeature},
 * 2) features that the framework supports and the ImsService should register for, and
 * 2) Additional "carrier" features, which are nonstandard registration strings needed for
 * carrier specific features.
 * @hide
 */
public class ImsRegistrationConfiguration implements Parcelable {
    /**
     * Features that this ImsService supports.
     */
    public final int[] features;
    /**
     * Features that the platform supports for Registration purposes that this ImsService does not
     * support.
     */
    public final int[] externalFeatures;
    /**
     * A list of custom carrier feature strings that are carrier specific and are needed for
     * Registration.
     */
    public final String[] carrierFeatures;

    /**
     * Configuration of the ImsService, which describes which features the ImsService supports
     * (for registration).
     * @param features an array of feature integers defined in {@link ImsFeature} that describe
     * which features this ImsService supports.
     */
    public ImsRegistrationConfiguration(int[] features) {
        this.features = features;
        this.externalFeatures = null;
        this.carrierFeatures = null;
    }

    /**
     * Configuration of the ImsService, which describes which features the ImsService supports
     * (for registration).
     * @param features an array of feature integers defined in {@link ImsFeature} that describe
     * which features this ImsService supports.
     * @param carrierFeatures A list of carrier specific feature strings for registration purposes.
     */
    public ImsRegistrationConfiguration(int[] features, String[] carrierFeatures) {
        this.features = features;
        this.externalFeatures = null;
        this.carrierFeatures = carrierFeatures;
    }

    public ImsRegistrationConfiguration(int[] features, int[] externalFeatures, String[] carrierFeatures) {
        this.features = features;
        this.externalFeatures = externalFeatures;
        this.carrierFeatures = carrierFeatures;
    }

    protected ImsRegistrationConfiguration(Parcel in) {
        features = in.createIntArray();
        this.externalFeatures = in.createIntArray();
        carrierFeatures = in.readStringArray();
    }

    public static final Creator<ImsRegistrationConfiguration> CREATOR = new Creator<ImsRegistrationConfiguration>() {
        @Override
        public ImsRegistrationConfiguration createFromParcel(Parcel in) {
            return new ImsRegistrationConfiguration(in);
        }

        @Override
        public ImsRegistrationConfiguration[] newArray(int size) {
            return new ImsRegistrationConfiguration[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(features);
        dest.writeIntArray(externalFeatures);
        dest.writeStringArray(carrierFeatures);
    }
}
