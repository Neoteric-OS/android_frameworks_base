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
    @IntDef(prefix = "SESSION_TYPE", value = {
        SESSION_TYPE_DECODE_ONLY,
        SESSION_TYPE_ENCODE_ONLY,
        SESSION_TYPE_ENCODE_AND_DECODE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecSessionType {};

    public static final int SOURCE_CODEC_TYPE_LC3 = 0;
    public static final int SOURCE_CODEC_TYPE_INVALID = 1000 * 1000;
    public static final int SESSION_TYPE_ENCODE_AND_DECODE = 0;
    public static final int SESSION_TYPE_ENCODE_ONLY = 1;
    public static final int SESSION_TYPE_DECODE_ONLY = 2;

    /**
     * Represents the count of valid source codec types. Can be accessed via
     * {@link #getMaxCodecType}.
     */
    private static final int SOURCE_CODEC_TYPE_MAX = 1;

    private final @SourceCodecType int mCodecType;

    /**
     * Indicates the codec config is for encode or decode session.
     */
    private final @CodecSessionType int mCodecSessionType;

    /**
     * Creates a new BluetoothLeAudioCodecConfig.
     *
     * @param codecType the source codec type
     * @param sessionType the session type of this source codec type
     */
    private BluetoothLeAudioCodecConfig(@SourceCodecType int codecType,
                                        @CodecSessionType int sessionType) {
        mCodecType = codecType;
        mCodecSessionType = sessionType;
    }

    @Override
    public String toString() {
        String sessionTypeStr;
        switch (mCodecSessionType) {
            case SESSION_TYPE_DECODE_ONLY:
                sessionTypeStr = "DECODE SESSION";
                break;
            case SESSION_TYPE_ENCODE_ONLY:
                sessionTypeStr = "ENCODE SESSION";
                break;
            case SESSION_TYPE_ENCODE_AND_DECODE:
                sessionTypeStr = "ENCODE AND DECODE SESSION";
                break;
            default:
                sessionTypeStr = "UNKNOWN SESSION TYPE(" + mCodecSessionType + ")";
                break;
        }

        return "{codecName:" + getCodecName()
                + ", direction:" + sessionTypeStr + "}";
    }

    /**
     * Gets the codec type.
     *
     * @return the codec type
     */
    public @SourceCodecType int getCodecType() {
        return mCodecSessionType;
    }

    /**
     * Gets the session type.
     *
     * @return the session type
     */
    public @SourceCodecType int getCodecSessionType() {
        return mCodecSessionType;
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
     * Builder for {@link BluetoothLeAudioCodecConfig}.
     * <p> By default, the codec type will be set to
     * {@link BluetoothLeAudioCodecConfig#SOURCE_CODEC_TYPE_INVALID}
     */
    public static final class Builder {
        private int mCodecType = BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID;
        private int mCodecSessionType = BluetoothLeAudioCodecConfig.SESSION_TYPE_ENCODE_AND_DECODE;

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
         * Set session type for Bluetooth codec config.
         *
         * @param sessionType of this codec
         * @return the same Builder instance
         */
        public @NonNull Builder setCodecSessionType(@CodecSessionType int sessionType) {
            mCodecSessionType = sessionType;
            return this;
        }
        /**
         * Build {@link BluetoothLeAudioCodecConfig}.
         * @return new BluetoothLeAudioCodecConfig built
         */
        public @NonNull BluetoothLeAudioCodecConfig build() {
            return new BluetoothLeAudioCodecConfig(mCodecType, mCodecSessionType);
        }
    }
}
