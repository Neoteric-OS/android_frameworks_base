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
 * limitations under the License.
 */

package android.os;

/**
 * VibrationAmplitude describes the strength applied to individual motors
 * within a haptic device for a {@link android.os.VibrationEffect}.
 */
public class VibrationAmplitude implements Parcelable {
    /**
     * The amplitude of the low-frequency 'heavy' motor
     */
    public final int strong;

    /**
     * The amplitude of the high-frequency 'light' motor
     */
    public final int weak;

    /**
     * Get an amplitude value for single motor devices.
     * @return If strong amplitude is zero, return weak amplitude. Otherwise
     * return strong amplitude
     */
    public int getSingleAmplitude() {
        if (strong == 0) {
            return weak;
        }
        return strong;
    }

    /**
     * Throw exception if amplitude is invalid
     * @hide
     */
    public void validate(boolean allowZeroAmplitude) {
        if (!allowZeroAmplitude && strong == 0 && weak == 0) {
            throw new IllegalArgumentException(
                    "amplitude components cannot both be zero.");
        }
        if (strong < -1 || weak < -1 || strong > 255 || weak > 255) {
            throw new IllegalArgumentException(
                    "amplitude components must either be DEFAULT_AMPLITUDE "
                    + "or between 0 and 255 inclusive");
        }
    }

    /** @hide to prevent subclassing outside framework */
    public VibrationAmplitude(int strong, int weak) {
        this.strong = strong;
        this.weak = weak;
    }

    /** @hide to prevent subclassing outside framework */
    public VibrationAmplitude() {
        this.strong = VibrationEffect.DEFAULT_AMPLITUDE;
        this.weak = VibrationEffect.DEFAULT_AMPLITUDE;
    }

    /**
     * Create a VibrationAmplitude with a single amplitude for simple
     * haptic devices such as the vibrator of a phone.
     * @param amplitude amplitude of motor
     *
     * @return desired amplitude
     */
    public static VibrationAmplitude create(int amplitude) {
        return new VibrationAmplitude(amplitude, amplitude);
    }

    /**
     * Create a VibrationAmplitude with default amplitude
     *
     * @return default amplitude
     */
    public static VibrationAmplitude createDefault() {
        return new VibrationAmplitude(VibrationEffect.DEFAULT_AMPLITUDE,
                VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /**
     * Create a VibrationAmplitude with split amplitudes for more complex
     * haptic devices such as the rumble motors of a game controller.
     * @param strong amplitude of motor which creates low-frequency, strong
     * vibrations
     * @param weak amplitude of motor which create high-frequency, light
     * vibrations
     *
     * @return desired split amplitude
     */
    public static VibrationAmplitude createSplit(int strong, int weak) {
        return new VibrationAmplitude(strong, weak);
    }

    @Override
    public String toString() {
        return "VibrationAmplitude{strong=" + strong + ",weak=" + weak + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VibrationAmplitude)) {
            return false;
        }
        VibrationAmplitude other = (VibrationAmplitude) o;
        return other.strong == strong && other.weak == weak;
    }

    @Override
    public int hashCode() {
        return (37 * strong) ^ (37 * weak);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(strong);
        out.writeInt(weak);
    }

    public static final Parcelable.Creator<VibrationAmplitude> CREATOR =
            new Parcelable.Creator<VibrationAmplitude>() {
                @Override
                public VibrationAmplitude createFromParcel(Parcel in) {
                    int strong = in.readInt();
                    int weak = in.readInt();
                    return new VibrationAmplitude(strong, weak);
                }

                @Override
                public VibrationAmplitude[] newArray(int size) {
                    return new VibrationAmplitude[size];
                }
            };
}
