/**
 * Copyright 2020 The Android Open Source Project
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

package android.telephony.data;

import android.annotation.CallSuper;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;


/**
 * Class that stores information specific to QOS.
 * @hide
 */
public abstract class Qos implements Parcelable{

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "QOS_TYPE_",
            value = {QOS_TYPE_EPS, QOS_TYPE_NR})
    public @interface QosType {}

    @QosType
    final int type;

    static final int QOS_TYPE_EPS = 1;
    static final int QOS_TYPE_NR = 2;

    QosBandwidth downlink;
    QosBandwidth uplink;

    Qos(int type,
            @NonNull android.hardware.radio.V1_6.QosBandwidth downlink,
            @NonNull android.hardware.radio.V1_6.QosBandwidth uplink) {
        this.type = type;
        this.downlink = new QosBandwidth(downlink.maxBitrateKbps, downlink.guaranteedBitrateKbps);
        this.uplink = new QosBandwidth(uplink.maxBitrateKbps, uplink.guaranteedBitrateKbps);
    }

    static class QosBandwidth implements Parcelable{
        int maxBitrateKbps;
        int guaranteedBitrateKbps;

        QosBandwidth(int maxBitrateKbps, int guaranteedBitrateKbps) {
            this.maxBitrateKbps = maxBitrateKbps;
            this.guaranteedBitrateKbps = guaranteedBitrateKbps;
        }

        private QosBandwidth(Parcel source) {
            this.maxBitrateKbps = source.readInt();
            this.guaranteedBitrateKbps = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(maxBitrateKbps);
            dest.writeInt(guaranteedBitrateKbps);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxBitrateKbps, guaranteedBitrateKbps);
        }

        @Override
        public String toString() {
            return "Bandwidth {"
                    + " maxBitrateKbps=" + maxBitrateKbps
                    + " guaranteedBitrateKbps=" + guaranteedBitrateKbps + "}";
        }

        public static final @NonNull Parcelable.Creator<QosBandwidth> CREATOR =
                new Parcelable.Creator<QosBandwidth>() {
                    @Override
                    public QosBandwidth createFromParcel(Parcel source) {
                        return new QosBandwidth(source);
                    }

                    @Override
                    public QosBandwidth[] newArray(int size) {
                        return new QosBandwidth[size];
                    }
                };
    };

    protected Qos(Parcel source) {
        this.type = source.readInt();
        this.downlink = source.readParcelable(QosBandwidth.class.getClassLoader());
        this.uplink = source.readParcelable(QosBandwidth.class.getClassLoader());
    }

    /**
     * Used by child classes for parceling.
     *
     * @hide
     */
    @CallSuper
    public void writeToParcel(@QosType int type, Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeParcelable(downlink, flags);
        dest.writeParcelable(uplink, flags);
    }

    public static Qos create(@NonNull android.hardware.radio.V1_6.Qos qos) {
        switch (qos.getDiscriminator()) {
            case android.hardware.radio.V1_6.Qos.hidl_discriminator.eps: return new EpsQos(qos.eps());
            case android.hardware.radio.V1_6.Qos.hidl_discriminator.nr: return new NrQos(qos.nr());
            default: return null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(downlink, uplink);
    }

    public static final @NonNull Parcelable.Creator<Qos> CREATOR =
            new Parcelable.Creator<Qos>() {
                @Override
                public Qos createFromParcel(Parcel source) {
                    int type = source.readInt();
                    switch (type) {
                        case QOS_TYPE_EPS: return EpsQos.createFromParcelBody(source);
                        case QOS_TYPE_NR: return NrQos.createFromParcelBody(source);
                        default: throw new IllegalArgumentException("Bad Qos Parcel");
                    }
                }

                @Override
                public Qos[] newArray(int size) {
                    return new Qos[size];
                }
            };
}
