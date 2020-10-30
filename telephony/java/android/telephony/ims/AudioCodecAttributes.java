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

package android.telephony.ims;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable object to handle audio codec attributes.
 * It provides the audio codec bitrate, bandwidth and their upper/lower bound.
 *
 * @hide
 */
@SystemApi
public final class AudioCodecAttributes implements Parcelable {
    // The audio codec bitrate in kbps.
    private double mAudioCodecBitrate;
    // The lower bound of the audio codec bitrate in kbps.
    private double mAudioCodecBitrateLower;
    // The upper bound of the audio codec bitrate in kbps.
    private double mAudioCodecBitrateUpper;
    // The audio codec bandwidth in kHz.
    private double mAudioCodecBandwidth;
    // The lower bound of the audio codec bandwidth in kHz.
    private double mAudioCodecBandwidthLower;
    // The upper bound of the audio codec bandwidth in kHz.
    private double mAudioCodecBandwidthUpper;

    /**
     * Constructor.
     *
     * @param audioCodecBitrate The audio codec bitrate in kbps.
     * @param audioCodecBitrateLower The lower bound of the audio codec bitrate in kbps.
     * @param audioCodecBitrateUpper The upper bound of the audio codec bitrate in kbps.
     * @param audioCodecBandwidth The audio codec bandwidth in kHz.
     * @param audioCodecBandwidthLower The lower bound of the audio codec bandwidth in kHz.
     * @param audioCodecBandwidthUpper The upper bound of the audio codec bandwidth in kHz.
     */
    public AudioCodecAttributes(double audioCodecBitrate, double audioCodecBitrateLower,
            double audioCodecBitrateUpper, double audioCodecBandwidth,
            double audioCodecBandwidthLower, double audioCodecBandwidthUpper) {
        mAudioCodecBitrate = audioCodecBitrate;
        mAudioCodecBitrateLower = audioCodecBitrateLower;
        mAudioCodecBitrateUpper = audioCodecBitrateUpper;
        mAudioCodecBandwidth = audioCodecBandwidth;
        mAudioCodecBandwidthLower = audioCodecBandwidthLower;
        mAudioCodecBandwidthUpper = audioCodecBandwidthUpper;
    }

    private AudioCodecAttributes(Parcel in) {
        mAudioCodecBitrate = in.readDouble();
        mAudioCodecBitrateLower = in.readDouble();
        mAudioCodecBitrateUpper = in.readDouble();
        mAudioCodecBandwidth = in.readDouble();
        mAudioCodecBandwidthLower = in.readDouble();
        mAudioCodecBandwidthUpper = in.readDouble();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeDouble(mAudioCodecBitrate);
        out.writeDouble(mAudioCodecBitrateLower);
        out.writeDouble(mAudioCodecBitrateUpper);
        out.writeDouble(mAudioCodecBandwidth);
        out.writeDouble(mAudioCodecBandwidthLower);
        out.writeDouble(mAudioCodecBandwidthUpper);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Creator<AudioCodecAttributes> CREATOR =
            new Creator<AudioCodecAttributes>() {
                @Override
                public AudioCodecAttributes createFromParcel(Parcel in) {
                    return new AudioCodecAttributes(in);
                }

                @Override
                public AudioCodecAttributes[] newArray(int size) {
                    return new AudioCodecAttributes[size];
                }
            };

    /**
     * @return the exact value of the audio codec bitrate in kbps.
     */
    public double getAudioCodecBitrate() {
        return mAudioCodecBitrate;
    }

    /**
     * @return the lower bound of the audio codec bitrate in kbps.
     */
    public double getAudioCodecBitrateLower() {
        return mAudioCodecBitrateLower;
    }

    /**
     * @return the upper bound of the audio codec bitrate in kbps.
     */
    public double getAudioCodecBitrateUpper() {
        return mAudioCodecBitrateUpper;
    }

    /**
     * @return the exact value of the audio codec bandwidth in kHz.
     */
    public double getAudioCodecBandwidth() {
        return mAudioCodecBandwidth;
    }

    /**
     * @return the lower bound of the audio codec bandwidth in kHz.
     */
    public double getAudioCodecBandwidthLower() {
        return mAudioCodecBandwidthLower;
    }

    /**
     * @return the upper bound of the audio codec bandwidth in kHz.
     */
    public double getAudioCodecBandwidthUpper() {
        return mAudioCodecBandwidthUpper;
    }

    @NonNull
    @Override
    public String toString() {
        return "{ audioCodecBitrate=" + mAudioCodecBitrate
                + ", audioCodecBitrateLower=" + mAudioCodecBitrateLower
                + ", audioCodecBitrateUpper=" + mAudioCodecBitrateUpper
                + ", audioCodecBandwidth=" + mAudioCodecBandwidth
                + ", audioCodecBandwidthLower=" + mAudioCodecBandwidthLower
                + ", audioCodecBandwidthUpper=" + mAudioCodecBandwidthUpper + " }";
    }
}
