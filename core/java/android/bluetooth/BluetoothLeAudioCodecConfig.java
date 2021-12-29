/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.bluetooth;

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the codec configuration for a Bluetooth LE Audio source device.
 * <p>Contains the source codec type.
 * <p>The source codec type values are the same as those supported by the
 * device hardware.
 *
 * {@see BluetoothLeAudioCodecConfig}
 */
public final class BluetoothLeAudioCodecConfig {
    // Add an entry for each source codec here.

    /** @hide */
    @IntDef(prefix = "SOURCE_CODEC_TYPE_", value = {
            SOURCE_CODEC_TYPE_LC3,
            SOURCE_CODEC_TYPE_INVALID
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SourceCodecType {};

    /** @hide */
    @IntDef(prefix = "AUDIO_DIRECTION", value = {
        AUDIO_DIRECTION_UNSPECIFIED,
        AUDIO_DIRECTION_INPUT,
        AUDIO_DIRECTION_OUTPUT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioDirectionType {};

    public static final int SOURCE_CODEC_TYPE_LC3 = 0;
    public static final int SOURCE_CODEC_TYPE_INVALID = 1000 * 1000;
    public static final int AUDIO_DIRECTION_UNSPECIFIED = 0;
    public static final int AUDIO_DIRECTION_INPUT = 1;
    public static final int AUDIO_DIRECTION_OUTPUT = 2;

    /**
     * Represents the count of valid source codec types. Can be accessed via
     * {@link #getMaxCodecType}.
     */
    private static final int SOURCE_CODEC_TYPE_MAX = 1;

    private final @SourceCodecType int mCodecType;

    /**
     * Indicates the codec is for encode or decode direction
     */
    private final @AudioDirectionType int mAudioDirection;

    /**
     * Creates a new BluetoothLeAudioCodecConfig.
     *
     * @param codecType the source codec type
     * @param audioDirection the audio direction of this source codec type
     */
    private BluetoothLeAudioCodecConfig(@SourceCodecType int codecType,
                                        @AudioDirectionType int audioDirection) {
        mCodecType = codecType;
        mAudioDirection = audioDirection;
    }

    @Override
    public String toString() {
        return "{codecName:" + getCodecName() + ", direction:" + getAudioDirectionName() + "}";
    }

    /**
     * Gets the codec type.
     *
     * @return the codec type
     */
    public @SourceCodecType int getCodecType() {
        return mCodecType;
    }

    /**
     * Gets the audio direction.
     *
     * @return the audio direction
     */
    public @SourceCodecType int getAudioDirection() {
        return mAudioDirection;
    }

    /**
     * Returns the valid codec types count.
     */
    public static int getMaxCodecType() {
        return SOURCE_CODEC_TYPE_MAX;
    }

    /**
     * Gets the codec name.
     *
     * @return the codec name
     */
    public @NonNull String getCodecName() {
        switch (mCodecType) {
            case SOURCE_CODEC_TYPE_LC3:
                return "LC3";
            case SOURCE_CODEC_TYPE_INVALID:
                return "INVALID CODEC";
            default:
                break;
        }
        return "UNKNOWN CODEC(" + mCodecType + ")";
    }

    /**
     * Gets the codec name.
     *
     * @return the codec name
     */
    public @NonNull String getAudioDirectionName() {
        switch (mAudioDirection) {
            case AUDIO_DIRECTION_UNSPECIFIED:
                return "AUDIO DIRECTION UNSPECIFIED";
            case AUDIO_DIRECTION_INPUT:
                return "AUDIO DIRECTION INPUT(Decode)";
            case AUDIO_DIRECTION_OUTPUT:
                return "AUDIO DIRECTION OUTPUT(Encode)";
            default:
                break;
        }
        return "UNKNOWN AUDIO DIRECTION(" + mAudioDirection + ")";
    }

    /**
     * Builder for {@link BluetoothLeAudioCodecConfig}.
     * <p> By default, the codec type will be set to
     * {@link BluetoothLeAudioCodecConfig#SOURCE_CODEC_TYPE_INVALID}
     */
    public static final class Builder {
        private int mCodecType = BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID;
        private int mAudioDirection = BluetoothLeAudioCodecConfig.AUDIO_DIRECTION_UNSPECIFIED;

        /**
         * Set codec type for Bluetooth codec config.
         *
         * @param codecType of this codec
         * @return the same Builder instance
         */
        public @NonNull Builder setCodecType(@SourceCodecType int codecType) {
            mCodecType = codecType;
            return this;
        }

        /**
         * Set audio direction for Bluetooth codec config.
         *
         * @param direction of this codec
         * @return the same Builder instance
         */
        public @NonNull Builder setAudioDirection(@AudioDirectionType int direction) {
            mAudioDirection = direction;
            return this;
        }
        /**
         * Build {@link BluetoothLeAudioCodecConfig}.
         * @return new BluetoothLeAudioCodecConfig built
         */
        public @NonNull BluetoothLeAudioCodecConfig build() {
            return new BluetoothLeAudioCodecConfig(mCodecType, mAudioDirection);
        }
    }
}
