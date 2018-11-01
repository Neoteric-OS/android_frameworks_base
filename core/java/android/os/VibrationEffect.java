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

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.vibrator.V1_0.EffectStrength;
import android.hardware.vibrator.V1_2.Effect;
import android.net.Uri;
import android.util.MathUtils;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A VibrationEffect describes a haptic effect to be performed by a {@link Vibrator}.
 *
 * These effects may be any number of things, from single shot vibrations to complex waveforms.
 */
public abstract class VibrationEffect implements Parcelable {
    private static final int PARCEL_TOKEN_ONE_SHOT = 1;
    private static final int PARCEL_TOKEN_WAVEFORM = 2;
    private static final int PARCEL_TOKEN_EFFECT = 3;

    /**
     * The default vibration strength of the device.
     */
    public static final int DEFAULT_AMPLITUDE = -1;

    /**
     * The maximum amplitude value
     * @hide
     */
    public static final int MAX_AMPLITUDE = 255;

    /**
     * A click effect.
     *
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_CLICK = Effect.CLICK;

    /**
     * A double click effect.
     *
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_DOUBLE_CLICK = Effect.DOUBLE_CLICK;

    /**
     * A tick effect.
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_TICK = Effect.TICK;

    /**
     * A thud effect.
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_THUD = Effect.THUD;

    /**
     * A pop effect.
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_POP = Effect.POP;

    /**
     * A heavy click effect.
     * @see #get(int)
     * @hide
     */
    public static final int EFFECT_HEAVY_CLICK = Effect.HEAVY_CLICK;


    /**
     * Ringtone patterns. They may correspond with the device's ringtone audio, or may just be a
     * pattern that can be played as a ringtone with any audio, depending on the device.
     *
     * @see #get(Uri, Context)
     * @hide
     */
    @VisibleForTesting
    public static final int[] RINGTONES = {
        Effect.RINGTONE_1,
        Effect.RINGTONE_2,
        Effect.RINGTONE_3,
        Effect.RINGTONE_4,
        Effect.RINGTONE_5,
        Effect.RINGTONE_6,
        Effect.RINGTONE_7,
        Effect.RINGTONE_8,
        Effect.RINGTONE_9,
        Effect.RINGTONE_10,
        Effect.RINGTONE_11,
        Effect.RINGTONE_12,
        Effect.RINGTONE_13,
        Effect.RINGTONE_14,
        Effect.RINGTONE_15
    };

    /** @hide to prevent subclassing from outside of the framework */
    public VibrationEffect() { }

    /**
     * Create a one shot vibration.
     *
     * One shot vibrations will vibrate constantly for the specified period of time at the
     * specified amplitude, and then stop.
     *
     * @param milliseconds The number of milliseconds to vibrate. This must be a positive number.
     * @param amplitude The strength of the vibration. This must be a value between 1 and 255, or
     * {@link #DEFAULT_AMPLITUDE}.
     *
     * @return The desired effect.
     */
    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        VibrationEffect effect =
                new OneShot(VibrationElement.create(milliseconds, amplitude));
        effect.validate();
        return effect;
    }

    /**
     * Create a one shot vibration.
     *
     * One shot vibrations will vibrate constantly for the period and channel amplitudes specified
     * by the {@link android.os.VibrationElement}.
     *
     * @param element The {@link android.os.VibrationElement} defining the effect.
     *
     * @return The desired effect.
     */
    public static VibrationEffect createOneShot(VibrationElement element) {
        VibrationEffect effect = new OneShot(element);
        effect.validate();
        return effect;
    }

    /**
     * Create a waveform vibration.
     *
     * Waveform vibrations are a potentially repeating series of timing and amplitude pairs. For
     * each pair, the value in the amplitude array determines the strength of the vibration and the
     * value in the timing array determines how long it vibrates for. An amplitude of 0 implies no
     * vibration (i.e. off), and any pairs with a timing value of 0 will be ignored.
     * <p>
     * The amplitude array of the generated waveform will be the same size as the given
     * timing array with alternating values of 0 (i.e. off) and {@link #DEFAULT_AMPLITUDE},
     * starting with 0. Therefore the first timing value will be the period to wait before turning
     * the vibrator on, the second value will be how long to vibrate at {@link #DEFAULT_AMPLITUDE}
     * strength, etc.
     * </p><p>
     * To cause the pattern to repeat, pass the index into the timings array at which to start the
     * repetition, or -1 to disable repeating.
     * </p>
     *
     * @param timings The pattern of alternating on-off timings, starting with off. Timing values
     *                of 0 will cause the timing / amplitude pair to be ignored.
     * @param repeat The index into the timings array at which to repeat, or -1 if you you don't
     *               want to repeat.
     *
     * @return The desired effect.
     */
    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        int[] amplitudes = new int[timings.length];
        for (int i = 0; i < (timings.length / 2); i++) {
            amplitudes[i*2 + 1] = VibrationEffect.DEFAULT_AMPLITUDE;
        }
        return createWaveform(timings, amplitudes, repeat);
    }

    /**
     * Create a waveform vibration.
     *
     * Waveform vibrations are a potentially repeating series of timing and amplitude pairs. For
     * each pair, the value in the amplitude array determines the strength of the vibration and the
     * value in the timing array determines how long it vibrates for. An amplitude of 0 implies no
     * vibration (i.e. off), and any pairs with a timing value of 0 will be ignored.
     * </p><p>
     * To cause the pattern to repeat, pass the index into the timings array at which to start the
     * repetition, or -1 to disable repeating.
     * </p>
     *
     * @param timings The timing values of the timing / amplitude pairs. Timing values of 0
     *                will cause the pair to be ignored.
     * @param amplitudes The amplitude values of the timing / amplitude pairs. Amplitude values
     *                   must be between 0 and 255, or equal to {@link #DEFAULT_AMPLITUDE}. An
     *                   amplitude value of 0 implies the motor is off.
     * @param repeat The index into the timings array at which to repeat, or -1 if you you don't
     *               want to repeat.
     *
     * @return The desired effect.
     */
    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        VibrationEffect effect = new Waveform(IntStream.range(0, timings.length)
                .mapToObj(i -> VibrationElement.create(timings[i], amplitudes[i]))
                .toArray(VibrationElement[]::new), repeat);
        effect.validate();
        return effect;
    }

    /**
     * Create a waveform vibration.
     *
     * Waveform vibrations are a potentially repeating series of
     * {@link android.os.VibrationElement}. Each element determines the per channel strength of the
     * vibration and how long it vibrates for. An amplitude of 0 implies no vibration (i.e. off),
     * and any elements with a duration value of 0 will be ignored.
     * </p><p>
     * To cause the pattern to repeat, pass the index into the elements array at which to start the
     * repetition, or -1 to disable repeating.
     * </p>
     *
     * @param elements The array of {@link android.os.VibrationElement} representing the waveform.
     * @param repeat The index into the timings array at which to repeat, or -1 if you you don't
     *               want to repeat.
     *
     * @return The desired effect.
     */
    public static VibrationEffect createWaveform(VibrationElement[] elements, int repeat) {
        VibrationEffect effect = new Waveform(elements, repeat);
        effect.validate();
        return effect;
    }

    /**
     * Get a predefined vibration effect.
     *
     * Predefined effects are a set of common vibration effects that should be identical, regardless
     * of the app they come from, in order to provide a cohesive experience for users across
     * the entire device. They also may be custom tailored to the device hardware in order to
     * provide a better experience than you could otherwise build using the generic building
     * blocks.
     *
     * This will fallback to a generic pattern if one exists and there does not exist a
     * hardware-specific implementation of the effect.
     *
     * @param effectId The ID of the effect to perform:
     *                 {@link #EFFECT_CLICK}, {@link #EFFECT_DOUBLE_CLICK}, {@link #EFFECT_TICK}
     *
     * @return The desired effect.
     * @hide
     */
    public static VibrationEffect get(int effectId) {
        return get(effectId, true);
    }

    /**
     * Get a predefined vibration effect.
     *
     * Predefined effects are a set of common vibration effects that should be identical, regardless
     * of the app they come from, in order to provide a cohesive experience for users across
     * the entire device. They also may be custom tailored to the device hardware in order to
     * provide a better experience than you could otherwise build using the generic building
     * blocks.
     *
     * Some effects you may only want to play if there's a hardware specific implementation because
     * they may, for example, be too disruptive to the user without tuning. The {@code fallback}
     * parameter allows you to decide whether you want to fallback to the generic implementation or
     * only play if there's a tuned, hardware specific one available.
     *
     * @param effectId The ID of the effect to perform:
     *                 {@link #EFFECT_CLICK}, {@link #EFFECT_DOUBLE_CLICK}, {@link #EFFECT_TICK}
     * @param fallback Whether to fallback to a generic pattern if a hardware specific
     *                 implementation doesn't exist.
     *
     * @return The desired effect.
     * @hide
     */
    public static VibrationEffect get(int effectId, boolean fallback) {
        VibrationEffect effect = new Prebaked(effectId, fallback);
        effect.validate();
        return effect;
    }

    /**
     * Get a predefined vibration effect associated with a given URI.
     *
     * Predefined effects are a set of common vibration effects that should be identical, regardless
     * of the app they come from, in order to provide a cohesive experience for users across
     * the entire device. They also may be custom tailored to the device hardware in order to
     * provide a better experience than you could otherwise build using the generic building
     * blocks.
     *
     * @param uri The URI associated with the haptic effect.
     * @param context The context used to get the URI to haptic effect association.
     *
     * @return The desired effect, or {@code null} if there's no associated effect.
     *
     * @hide
     */
    @Nullable
    public static VibrationEffect get(Uri uri, Context context) {
        String[] uris = context.getResources().getStringArray(
                com.android.internal.R.array.config_ringtoneEffectUris);
        for (int i = 0; i < uris.length && i < RINGTONES.length; i++) {
            if (uris[i] == null) {
                continue;
            }
            ContentResolver cr = context.getContentResolver();
            Uri mappedUri = cr.uncanonicalize(Uri.parse(uris[i]));
            if (mappedUri == null) {
                continue;
            }
            if (mappedUri.equals(uri)) {
                return get(RINGTONES[i]);
            }
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    public abstract void validate();

    /**
     * Gets the estimated duration of the vibration in milliseconds.
     *
     * For effects without a defined end (e.g. a Waveform with a non-negative repeat index), this
     * returns Long.MAX_VALUE. For effects with an unknown duration (e.g. Prebaked effects where
     * the length is device and potentially run-time dependent), this returns -1.
     *
     * @hide
     */
    public abstract long getDuration();

    /**
     * Scale the amplitude with the given constraints.
     *
     * This assumes that the previous value was in the range [0, MAX_AMPLITUDE]
     * @hide
     */
    protected static int scale(int amplitude, float gamma, int maxAmplitude) {
        float val = MathUtils.pow(amplitude / (float) MAX_AMPLITUDE, gamma);
        return (int) (val * maxAmplitude);
    }

    /** @hide */
    public static class OneShot extends VibrationEffect implements Parcelable {
        private final VibrationElement mElement;

        public OneShot(Parcel in) {
            mElement = in.readTypedObject(VibrationElement.CREATOR);
        }

        public OneShot(VibrationElement element) {
            mElement = element;
        }

        @Override
        public long getDuration() {
            return mElement.duration;
        }

        public int getAmplitude() {
            return getAmplitude(VibrationElement.CHANNEL_DEFAULT);
        }

        /**
         * Get the amplitude of the corresponding channel.
         *
         * @param channel the channel being requested
         *
         * @return The amplitude of the requested channel.
         */
        public int getAmplitude(int channel) {
            return mElement.channels[channel];
        }

        public VibrationElement getElement() {
            return mElement;
        }

        /**
         * Scale the amplitude of this effect.
         *
         * @param gamma the gamma adjustment to apply
         * @param maxAmplitude the new maximum amplitude of the effect
         *
         * @return A {@link OneShot} effect with the same timing but scaled amplitude.
         */
        public VibrationEffect scale(float gamma, int maxAmplitude) {
            VibrationElement newElement = VibrationElement.scale(mElement, gamma, maxAmplitude);
            return new OneShot(newElement);
        }

        /**
         * Resolve default values into integer amplitude numbers.
         *
         * @param defaultAmplitude the default amplitude to apply, must be between 0 and
         *         MAX_AMPLITUDE
         * @return A {@link OneShot} effect with same physical meaning but explicitly set amplitude
         *
         * @hide
         */
        public OneShot resolve(int defaultAmplitude) {
            VibrationElement element = mElement.resolve(defaultAmplitude);
            if (element == mElement) {
                return this;
            }
            return new OneShot(element);
        }

        @Override
        public void validate() {
            if (IntStream.of(mElement.channels).anyMatch(a -> (a < -1 || a > 255))) {
                throw new IllegalArgumentException(
                        "channels must either be DEFAULT_AMPLITUDE, "
                        + "or between 0 and 255 inclusive");
            }
            if (IntStream.of(mElement.channels).noneMatch(a -> (a != 0))) {
                throw new IllegalArgumentException(
                        "at least one channel must either be DEFAULT_AMPLITUDE, "
                        + "or between 1 and 255 inclusive");
            }
            if (mElement.duration <= 0) {
                throw new IllegalArgumentException(
                        "duration must be positive (duration=" + mElement.duration + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.OneShot)) {
                return false;
            }
            VibrationEffect.OneShot other = (VibrationEffect.OneShot) o;
            return other.mElement.equals(mElement);
        }

        @Override
        public int hashCode() {
            return mElement.hashCode();
        }

        @Override
        public String toString() {
            return "OneShot{mElement=" + mElement + "}";
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_ONE_SHOT);
            out.writeTypedObject(mElement, flags);
        }

        public static final Parcelable.Creator<OneShot> CREATOR =
            new Parcelable.Creator<OneShot>() {
                @Override
                public OneShot createFromParcel(Parcel in) {
                    // Skip the type token
                    in.readInt();
                    return new OneShot(in);
                }
                @Override
                public OneShot[] newArray(int size) {
                    return new OneShot[size];
                }
            };
    }

    /** @hide */
    public static class Waveform extends VibrationEffect implements Parcelable {
        private final VibrationElement[] mElements;
        private final int mRepeat;

        public Waveform(Parcel in) {
            this(in.createTypedArray(VibrationElement.CREATOR), in.readInt());
        }

        public Waveform(VibrationElement[] elements, int repeat) {
            mElements = new VibrationElement[elements.length];
            System.arraycopy(elements, 0, mElements, 0, elements.length);
            mRepeat = repeat;
        }

        public long[] getTimings() {
            return Stream.of(mElements).mapToLong(e -> e.duration).toArray();
        }

        public int[] getAmplitudes() {
            return getAmplitudes(VibrationElement.CHANNEL_DEFAULT);
        }

        /**
         * Get the amplitude array of the corresponding channel.
         *
         * @param channel the channel being requested
         *
         * @return The amplitude array of the requested channel.
         */
        public int[] getAmplitudes(int channel) {
            return Stream.of(mElements).mapToInt(e -> e.channels[channel]).toArray();
        }

        public VibrationElement[] getElements() {
            return mElements;
        }

        public int getRepeatIndex() {
            return mRepeat;
        }

        @Override
        public long getDuration() {
            if (mRepeat >= 0) {
                return Long.MAX_VALUE;
            }
            return Stream.of(mElements).mapToLong(e -> e.duration).sum();
        }

        /**
         * Scale the Waveform with the given gamma and new max amplitude.
         *
         * @param gamma the gamma adjustment to apply
         * @param maxAmplitude the new maximum amplitude of the effect
         *
         * @return A {@link Waveform} effect with the same timings and repeat index
         *         but scaled amplitude.
         */
        public VibrationEffect scale(float gamma, int maxAmplitude) {
            if (gamma == 1.0f && maxAmplitude == MAX_AMPLITUDE) {
                // Just return a copy of the original if there's no scaling to be done.
                return new Waveform(mElements, mRepeat);
            }

            return new Waveform(Stream.of(mElements)
                .map(e -> VibrationElement.scale(e, gamma, maxAmplitude))
                .toArray(VibrationElement[]::new), mRepeat);
        }

        /**
         * Resolve default values into integer amplitude numbers.
         *
         * @param defaultAmplitude the default amplitude to apply, must be between 0 and
         *         MAX_AMPLITUDE
         * @return A {@link Waveform} effect with same physical meaning but explicitly set
         *         amplitude
         *
         * @hide
         */
        public Waveform resolve(int defaultAmplitude) {
            if (defaultAmplitude > MAX_AMPLITUDE || defaultAmplitude < 0) {
                throw new IllegalArgumentException(
                        "Amplitude is negative or greater than MAX_AMPLITUDE");
            }
            return new Waveform(Stream.of(mElements)
                    .map(e -> VibrationElement.resolve(e, defaultAmplitude))
                    .toArray(VibrationElement[]::new), mRepeat);
        }

        @Override
        public void validate() {
            if (Stream.of(mElements).noneMatch(e -> e.duration > 0)) {
                throw new IllegalArgumentException("at least one timing must be non-zero"
                        + " (elements=" + Arrays.toString(mElements) + ")");
            }
            if (Stream.of(mElements).anyMatch(e -> IntStream.of(e.channels)
                    .anyMatch(a -> (a < -1 || a > 255)))) {
                throw new IllegalArgumentException(
                        "amplitudes must all be DEFAULT_AMPLITUDE or between 0 and 255"
                        + " (elements=" + Arrays.toString(mElements) + ")");
            }
            if (mRepeat < -1 || mRepeat >= mElements.length) {
                throw new IllegalArgumentException(
                        "repeat index must be within the bounds of the elements array"
                        + " (elements.length=" + mElements.length + ", index=" + mRepeat + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.Waveform)) {
                return false;
            }
            VibrationEffect.Waveform other = (VibrationEffect.Waveform) o;
            return Arrays.equals(mElements, other.mElements)
                && mRepeat == other.mRepeat;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 37 * Arrays.hashCode(mElements);
            result += 37 * mRepeat;
            return result;
        }

        @Override
        public String toString() {
            return "Waveform{mElements=" + Arrays.toString(mElements)
                + ", mRepeat=" + mRepeat
                + "}";
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_WAVEFORM);
            out.writeTypedArray(mElements, flags);
            out.writeInt(mRepeat);
        }

        public static final Parcelable.Creator<Waveform> CREATOR =
            new Parcelable.Creator<Waveform>() {
                @Override
                public Waveform createFromParcel(Parcel in) {
                    // Skip the type token
                    in.readInt();
                    return new Waveform(in);
                }
                @Override
                public Waveform[] newArray(int size) {
                    return new Waveform[size];
                }
            };
    }

    /** @hide */
    public static class Prebaked extends VibrationEffect implements Parcelable {
        private final int mEffectId;
        private final boolean mFallback;

        private int mEffectStrength;

        public Prebaked(Parcel in) {
            this(in.readInt(), in.readByte() != 0);
            mEffectStrength = in.readInt();
        }

        public Prebaked(int effectId, boolean fallback) {
            mEffectId = effectId;
            mFallback = fallback;
            mEffectStrength = EffectStrength.MEDIUM;
        }

        public int getId() {
            return mEffectId;
        }

        /**
         * Whether the effect should fall back to a generic pattern if there's no hardware specific
         * implementation of it.
         */
        public boolean shouldFallback() {
            return mFallback;
        }

        @Override
        public long getDuration() {
            return -1;
        }

        /**
         * Set the effect strength of the prebaked effect.
         */
        public void setEffectStrength(int strength) {
            if (!isValidEffectStrength(strength)) {
                throw new IllegalArgumentException("Invalid effect strength: " + strength);
            }
            mEffectStrength = strength;
        }

        /**
         * Set the effect strength.
         */
        public int getEffectStrength() {
            return mEffectStrength;
        }

        private static boolean isValidEffectStrength(int strength) {
            switch (strength) {
                case EffectStrength.LIGHT:
                case EffectStrength.MEDIUM:
                case EffectStrength.STRONG:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void validate() {
            switch (mEffectId) {
                case EFFECT_CLICK:
                case EFFECT_DOUBLE_CLICK:
                case EFFECT_TICK:
                case EFFECT_THUD:
                case EFFECT_POP:
                case EFFECT_HEAVY_CLICK:
                    break;
                default:
                    if (mEffectId < RINGTONES[0] || mEffectId > RINGTONES[RINGTONES.length - 1]) {
                        throw new IllegalArgumentException(
                                "Unknown prebaked effect type (value=" + mEffectId + ")");
                    }
            }
            if (!isValidEffectStrength(mEffectStrength)) {
                throw new IllegalArgumentException(
                        "Unknown prebaked effect strength (value=" + mEffectStrength + ")");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VibrationEffect.Prebaked)) {
                return false;
            }
            VibrationEffect.Prebaked other = (VibrationEffect.Prebaked) o;
            return mEffectId == other.mEffectId
                && mFallback == other.mFallback
                && mEffectStrength == other.mEffectStrength;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 37 * mEffectId;
            result += 37 * mEffectStrength;
            return result;
        }

        @Override
        public String toString() {
            return "Prebaked{mEffectId=" + mEffectId
                + ", mEffectStrength=" + mEffectStrength
                + ", mFallback=" + mFallback
                + "}";
        }


        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_EFFECT);
            out.writeInt(mEffectId);
            out.writeByte((byte) (mFallback ? 1 : 0));
            out.writeInt(mEffectStrength);
        }

        public static final Parcelable.Creator<Prebaked> CREATOR =
            new Parcelable.Creator<Prebaked>() {
                @Override
                public Prebaked createFromParcel(Parcel in) {
                    // Skip the type token
                    in.readInt();
                    return new Prebaked(in);
                }
                @Override
                public Prebaked[] newArray(int size) {
                    return new Prebaked[size];
                }
            };
    }

    public static final Parcelable.Creator<VibrationEffect> CREATOR =
            new Parcelable.Creator<VibrationEffect>() {
                @Override
                public VibrationEffect createFromParcel(Parcel in) {
                    int token = in.readInt();
                    if (token == PARCEL_TOKEN_ONE_SHOT) {
                        return new OneShot(in);
                    } else if (token == PARCEL_TOKEN_WAVEFORM) {
                        return new Waveform(in);
                    } else if (token == PARCEL_TOKEN_EFFECT) {
                        return new Prebaked(in);
                    } else {
                        throw new IllegalStateException(
                                "Unexpected vibration event type token in parcel.");
                    }
                }
                @Override
                public VibrationEffect[] newArray(int size) {
                    return new VibrationEffect[size];
                }
            };
}
