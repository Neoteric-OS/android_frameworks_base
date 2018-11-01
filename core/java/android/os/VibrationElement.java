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

import android.util.MathUtils;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * A VibrationElement describes a component of a {@link VibrationEffect} to be performed by a
 * {@link Vibrator}.
 *
 * These effects may be any number of things, from single shot vibrations to complex waveforms.
 */
public class VibrationElement implements Parcelable {
    /**
     * Maximum channels in an element
     */
    public static final int MAX_CHANNELS = 256;

    /**
     * Default channel for the haptic device
     */
    public static final int CHANNEL_DEFAULT = 0;

    /**
     * Duration in milliseconds of this element
     */
    public final long duration;

    /**
     * Amplitude for each channel of the haptic device for this element
     */
    public final int[] channels;

    /** @hide */
    public VibrationElement(long duration, int[] channels) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0");
        }
        if (channels == null) {
            throw new IllegalArgumentException("channels must be non-null");
        }
        if (channels.length == 0 || channels.length > MAX_CHANNELS) {
            throw new IllegalArgumentException("channels must contain between 1 and MAX_CHANNELS "
                    + "elements");
        }

        this.duration = duration;
        this.channels = channels;
    }

    /**
     * Create a VibrationElement
     *
     * a VibrationElement defines a single action within a {@link VibrationEffect} pattern. It
     * defines a duration and the amplitudes of each effector channel for the action.
     * <p>
     * Various haptic devices have a different number of "channels" or "motors". In a phone, it's
     * vibration motor is considered a single channel device. Devices such as gamepads may have two
     * motors, where one creates a low frequency, "strong" vibration and the other generates a high
     * frequency, "weak" vibration.
     * </p><p>
     * If a multiple channel device is provided with a single channel VibrationElement, all motors
     * within the device will use the value provided by the singular channel.
     * </p>
     *
     * @param duration The duration of the element in milliseconds.
     * @param channels The amplitudes for each channel of the element. If none are provided, an
     * element with {@link VibrationElement#MAX_CHANNELS} channels is created with each channel set
     * to {@link VibrationEffect#DEFAULT_AMPLITUDE}.
     *
     * @return element for use with {@link VibrationEffect}.
     */
    public static VibrationElement create(long duration, int... channels) {
        if (channels.length == 0) {
            int[] defaultChannels = new int[MAX_CHANNELS];
            Arrays.fill(defaultChannels, VibrationEffect.DEFAULT_AMPLITUDE);
            return new VibrationElement(duration, defaultChannels);
        }
        return new VibrationElement(duration, channels);
    }

    private static int scale(int channel, float gamma, int maxAmplitude) {
        return (int) (MathUtils.pow(channel / (float) VibrationEffect.MAX_AMPLITUDE, gamma)
                * maxAmplitude);
    }

    /** @hide */
    public static VibrationElement scale(VibrationElement element, float gamma, int maxAmplitude) {
        int[] scaledChannels = IntStream.of(element.channels)
                .map(a -> scale(a, gamma, maxAmplitude)).toArray();
        return new VibrationElement(element.duration, scaledChannels);
    }

    /** @hide */
    public static VibrationElement resolve(VibrationElement element, int defaultAmplitude) {
        VibrationElement resolvedElement = new VibrationElement(element.duration,
                element.channels.clone());
        resolvedElement.resolve(defaultAmplitude);
        return resolvedElement;
    }

    /** @hide */
    public VibrationElement resolve(int defaultAmplitude) {
        // abort early if no elements are the default amplitude
        if (IntStream.of(channels).noneMatch(a -> a == VibrationEffect.DEFAULT_AMPLITUDE)) {
            return this;
        }

        // validate default amplitude
        if (defaultAmplitude > VibrationEffect.MAX_AMPLITUDE || defaultAmplitude < 0) {
            throw new IllegalArgumentException(
                "amplitude components must either be DEFAULT_AMPLITUDE "
                + "or between 0 and MAX_AMPLITUDE (inclusive)");
        }

        // return a new element with the default amplitude channels resolved
        return new VibrationElement(duration, IntStream.of(channels).map(
                a -> (a == VibrationEffect.DEFAULT_AMPLITUDE ? defaultAmplitude : a)).toArray());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VibrationElement)) {
            return false;
        }
        VibrationElement other = (VibrationElement) o;
        return other.duration == duration && Arrays.equals(other.channels, channels);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result += 37 * 37 * Long.hashCode(duration);
        result += 37 * Arrays.hashCode(channels);
        return result;
    }

    @Override
    public String toString() {
        return "VibrationElement{duration=" + duration
                + ", channels=" + Arrays.toString(channels) + "}";
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(duration);
        out.writeIntArray(channels);
    }

    public static final Parcelable.Creator<VibrationElement> CREATOR =
            new Parcelable.Creator<VibrationElement>() {
                @Override
                public VibrationElement createFromParcel(Parcel in) {
                    long duration = in.readLong();
                    int[] channels = in.createIntArray();
                    return new VibrationElement(duration, channels);
                }

                @Override
                public VibrationElement[] newArray(int size) {
                    return new VibrationElement[size];
                }
            };
}
