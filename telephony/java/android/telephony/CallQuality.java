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

package android.telephony;

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Parcelable object to handle call quality.
 * <p>
 * Currently this supports IMS calls.
 * <p>
 * It provides the call quality level, duration, and additional
 * information related to RTP packets, jitter and delay.
 *
 * @hide
 */
@SystemApi
public final class CallQuality implements Parcelable {

    // Constants representing the call quality level (see #CallQuality);
    public static final int CALL_QUALITY_EXCELLENT = 0;
    public static final int CALL_QUALITY_GOOD = 1;
    public static final int CALL_QUALITY_FAIR = 2;
    public static final int CALL_QUALITY_POOR = 3;
    public static final int CALL_QUALITY_BAD = 4;
    public static final int CALL_QUALITY_NOT_AVAILABLE = 5;

    // Constants representing the codec type (see #CodecType);
    public static final int CODEC_TYPE_EVS = 0;
    public static final int CODEC_TYPE_AMR = 1;
    public static final int CODEC_TYPE_WB_AMR = 2;

    /**
     * Call quality
     * @hide
     */
    @IntDef({
            CALL_QUALITY_EXCELLENT,
            CALL_QUALITY_GOOD,
            CALL_QUALITY_FAIR,
            CALL_QUALITY_POOR,
            CALL_QUALITY_BAD,
            CALL_QUALITY_NOT_AVAILABLE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallQualityLevel {}

    /**
     * Codec type
     * @hide
     */
    @IntDef({
            CODEC_TYPE_EVS,
            CODEC_TYPE_AMR,
            CODEC_TYPE_WB_AMR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecType {}

    @CallQualityLevel
    private int mCallQualityLevel;
    private int mCallDuration;
    private int mNumRtpPacketsTransmitted;
    private int mNumRtpPacketsReceived;
    private int mNumRtpPacketsNotTransmitted;
    private int mNumRtpPacketsNotReceived;
    private int mAverageRelativeJitter;
    private int mMaxRelativeJitter;
    private int mAverageRoundTripTime;
    @CodecType
    private int mCodecType;
    private int mCallId;

    /** @hide **/
    public CallQuality(Parcel in) {
        mCallQualityLevel = in.readInt();
        mCallDuration = in.readInt();
        mNumRtpPacketsTransmitted = in.readInt();
        mNumRtpPacketsReceived = in.readInt();
        mNumRtpPacketsNotTransmitted = in.readInt();
        mNumRtpPacketsNotReceived = in.readInt();
        mAverageRelativeJitter = in.readInt();
        mMaxRelativeJitter = in.readInt();
        mAverageRoundTripTime = in.readInt();
        mCodecType = in.readInt();
        mCallId = in.readInt();
    }

    /**
     * Constructor.
     *
     * @param callQualityLevel the call quality level (see #CallQualityLevel)
     * @param callDuration the call duration in milliseconds
     * @param numRtpPacketsTransmitted RTP packets sent to network
     * @param numRtpPacketsReceived RTP packets received from network
     * @param numRtpPacketsNotTransmitted RTP packets which lost in network and never transmitted
     * @param numRtpPacketsNotReceived RTP packets which were lost in network and never recieved
     * @param averageRelativeJitter average relative jitter in milliseconds
     * @param maxRelativeJitter maximum relative jitter in milliseconds
     * @param averageRoundTripTime average round trip delay in milliseconds
     * @param codecType the codec type (see #CodecType)
     * @param callId the call Id
     */
    public CallQuality(@CallQualityLevel int callQualityLevel,
            int callDuration,
            int numRtpPacketsTransmitted,
            int numRtpPacketsReceived,
            int numRtpPacketsNotTransmitted,
            int numRtpPacketsNotReceived,
            int averageRelativeJitter,
            int maxRelativeJitter,
            int averageRoundTripTime,
            @CodecType int codecType,
            int callId) {
        this.mCallDuration = callDuration;
        this.mNumRtpPacketsTransmitted = numRtpPacketsTransmitted;
        this.mNumRtpPacketsReceived = numRtpPacketsReceived;
        this.mNumRtpPacketsNotTransmitted = numRtpPacketsNotTransmitted;
        this.mNumRtpPacketsNotReceived = numRtpPacketsNotReceived;
        this.mAverageRelativeJitter = averageRelativeJitter;
        this.mMaxRelativeJitter = maxRelativeJitter;
        this.mAverageRoundTripTime = averageRoundTripTime;
        this.mCallId = callId;
    }

    // getters and setters
    @CallQualityLevel
    public int getCallQualityLevel() {
        return mCallQualityLevel;
    }

    public int getCallQualityDuration() {
        return mCallDuration;
    }

    public int getNumRtpPacketsTransmitted() {
        return mNumRtpPacketsTransmitted;
    }

    public int getNumRtpPacketsReceived() {
        return mNumRtpPacketsReceived;
    }

    public int getNumRtpPacketsNotTransmitted() {
        return mNumRtpPacketsNotTransmitted;
    }

    public int getNumRtpPacketsNotReceived() {
        return mNumRtpPacketsNotReceived;
    }

    public int getAverageRelativeJitter() {
        return mAverageRelativeJitter;
    }

    public int getMaxRelativeJitter() {
        return mMaxRelativeJitter;
    }

    public int getAverageRoundTripTime() {
        return mAverageRoundTripTime;
    }

    @CodecType
    public int getCodecType() {
        return mCodecType;
    }

    public int getCallId() {
        return mCallId;
    }

    public void setCallQualityLevel(@CallQualityLevel int callQualityLevel) {
        this.mCallQualityLevel = callQualityLevel;
    }

    public void setCallQualityDuration(int callDuration) {
        this.mCallDuration = callDuration;
    }

    public void setNumRtpPacketsTransmitted(int numRtpPacketsTransmitted) {
        this.mNumRtpPacketsTransmitted = numRtpPacketsTransmitted;
    }

    public void setNumRtpPacketsReceived(int numRtpPacketsReceived) {
        this.mNumRtpPacketsReceived = numRtpPacketsReceived;
    }

    public void setNumRtpPacketsNotTransmitted(int numRtpPacketsNotTransmitted) {
        this.mNumRtpPacketsNotTransmitted = numRtpPacketsNotTransmitted;
    }

    public void setNumRtpPacketsNotReceived(int numRtpPacketsNotReceived) {
        this.mNumRtpPacketsNotReceived = numRtpPacketsNotReceived;
    }

    public void setAverageRelativeJitter(int averageRelativeJitter) {
        this.mAverageRelativeJitter = averageRelativeJitter;
    }

    public void setMaxRelativeJitter(int maxRelativeJitter) {
        this.mMaxRelativeJitter = maxRelativeJitter;
    }

    public void setAverageRoundTripTime(int averageRoundTripTime) {
        this.mAverageRoundTripTime = averageRoundTripTime;
    }

    public void setCodecType(@CodecType int codecType) {
        this.mCodecType = codecType;
    }

    public void setCallId(int callId) {
        this.mCallId = callId;
    }

    // Parcelable things
    @Override
    public String toString() {
        return "callQualityLevel=" + mCallQualityLevel
                + " callDuration=" + mCallDuration
                + " numRtpPacketsTransmitted=" + mNumRtpPacketsTransmitted
                + " numRtpPacketsReceived=" + mNumRtpPacketsReceived
                + " numRtpPacketsNotTransmitted=" + mNumRtpPacketsNotTransmitted
                + " numRtpPacketsNotReceived=" + mNumRtpPacketsNotReceived
                + " averageRelativeJitter=" + mAverageRelativeJitter
                + " maxRelativeJitter=" + mMaxRelativeJitter
                + " averageRoundTripTime=" + mAverageRoundTripTime
                + " codecType=" + mCodecType
                + " callId=" + mCallId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCallQualityLevel,
                mCallDuration,
                mNumRtpPacketsTransmitted,
                mNumRtpPacketsReceived,
                mNumRtpPacketsNotTransmitted,
                mNumRtpPacketsNotReceived,
                mAverageRelativeJitter,
                mMaxRelativeJitter,
                mAverageRoundTripTime,
                mCodecType,
                mCallId);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CallQuality) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        CallQuality s = (CallQuality) o;

        return (mCallQualityLevel == s.mCallQualityLevel
                && mCallDuration == s.mCallDuration
                && mNumRtpPacketsTransmitted == s.mNumRtpPacketsTransmitted
                && mNumRtpPacketsReceived == s.mNumRtpPacketsReceived
                && mNumRtpPacketsNotTransmitted == s.mNumRtpPacketsNotTransmitted
                && mNumRtpPacketsNotReceived == s.mNumRtpPacketsNotReceived
                && mAverageRelativeJitter == s.mAverageRelativeJitter
                && mMaxRelativeJitter == s.mMaxRelativeJitter
                && mAverageRoundTripTime == s.mAverageRoundTripTime
                && mCodecType == s.mCodecType
                && mCallId == s.mCallId);
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public @Parcelable.ContentsFlags int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel dest, @Parcelable.WriteFlags int flags) {
        dest.writeInt(mCallQualityLevel);
        dest.writeInt(mCallDuration);
        dest.writeInt(mNumRtpPacketsTransmitted);
        dest.writeInt(mNumRtpPacketsReceived);
        dest.writeInt(mNumRtpPacketsNotTransmitted);
        dest.writeInt(mNumRtpPacketsNotReceived);
        dest.writeInt(mAverageRelativeJitter);
        dest.writeInt(mMaxRelativeJitter);
        dest.writeInt(mAverageRoundTripTime);
        dest.writeInt(mCodecType);
        dest.writeInt(mCallId);
    }

    public static final Parcelable.Creator<CallQuality> CREATOR = new Parcelable.Creator() {
        public CallQuality createFromParcel(Parcel in) {
            return new CallQuality(in);
        }

        public CallQuality[] newArray(int size) {
            return new CallQuality[size];
        }
    };
}
