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

package android.bluetooth;

import android.annotation.NonNull;
import android.compat.annotation.UnsupportedAppUsage;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.List;


/**
 * Represents the dynamic audio buffer time.
 *
 * {@hide}
 */
public final class BluetoothDynamicAudioBuffer implements Parcelable {
    /**
     * Indicates the supported type of Dynamic Audio Buffer is not supported.
     *
     */
    public static final int DYNAMIC_AUDIO_BUFFER_TYPE_NONE = 0;

    /**
     * Indicates the supported type of Dynamic Audio Buffer is A2DP offload.
     *
     */
    public static final int DYNAMIC_AUDIO_BUFFER_TYPE_A2DP_OFFLOAD = 1;

    /**
     * Indicates the supported type of Dynamic Audio Buffer is A2DP software encoding.
     *
     */
    public static final int DYNAMIC_AUDIO_BUFFER_TYPE_A2DP_SOFTWARE_ENCODING = 2;

    public static final int BUFFER_TYPE_DEFAULT_VALUE = 0;
    public static final int BUFFER_TYPE_MAXIMUM_VALUE = 1;
    public static final int BUFFER_TYPE_MINIMUM_VALUE = 2;
    public static final int BUFFER_TYPE_MAX = 3;

    public static final int BUFFER_CODEC_MAX_NUM = 32;

    private static final String TAG = "BluetoothDynamicAudioBuffer";

    private int[][] mDynamicAudioBufferCapabilities;
    private int mDynamicAudioBufferCodecNumber = BluetoothCodecConfig.SOURCE_CODEC_TYPE_MAX;

    public BluetoothDynamicAudioBuffer(@NonNull List<List<Integer>>
            dynamicAudioBufferCapabilitiesList) {

        mDynamicAudioBufferCapabilities = new int[BUFFER_CODEC_MAX_NUM][BUFFER_TYPE_MAX];

        for (int i = 0; i < BUFFER_CODEC_MAX_NUM; i++) {
            List<Integer> dynamicAudioBufferTimeList = dynamicAudioBufferCapabilitiesList.get(i);
            mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_DEFAULT_VALUE] =
                dynamicAudioBufferTimeList.get(BUFFER_TYPE_DEFAULT_VALUE).intValue();
            mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_MAXIMUM_VALUE] =
                dynamicAudioBufferTimeList.get(BUFFER_TYPE_MAXIMUM_VALUE).intValue();
            mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_MINIMUM_VALUE] =
                dynamicAudioBufferTimeList.get(BUFFER_TYPE_MINIMUM_VALUE).intValue();
        }

        for (int i = 0; i < BluetoothCodecConfig.SOURCE_CODEC_TYPE_MAX; i++) {
            Log.e(TAG, codecTypeToString(i) + " Default Buffer Time = "
                    + mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_DEFAULT_VALUE]);
            Log.e(TAG, codecTypeToString(i) + " Maximum Buffer Time = "
                    + mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_MAXIMUM_VALUE]);
            Log.e(TAG, codecTypeToString(i) + " Minimum Buffer Time = "
                    + mDynamicAudioBufferCapabilities[i][BUFFER_TYPE_MINIMUM_VALUE]);
        }
    }

    BluetoothDynamicAudioBuffer(Parcel in) {
        int[][] dynamicAudioBufferCapabilities;
        final int n = in.readInt();
        dynamicAudioBufferCapabilities = new int[n][];
        for (int i = 0; i < n; i++) {
            dynamicAudioBufferCapabilities[i] = in.createIntArray();
        }
        mDynamicAudioBufferCapabilities = dynamicAudioBufferCapabilities;
    }

    public static final @NonNull Parcelable.Creator<BluetoothDynamicAudioBuffer> CREATOR =
            new Parcelable.Creator<BluetoothDynamicAudioBuffer>() {
                public BluetoothDynamicAudioBuffer createFromParcel(Parcel in) {
                    return new BluetoothDynamicAudioBuffer(in);
                }

                public BluetoothDynamicAudioBuffer[] newArray(int size) {
                    return new BluetoothDynamicAudioBuffer[size];
                }
            };

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        final int n = mDynamicAudioBufferCapabilities.length;
        out.writeInt(n);
        for (int i = 0; i < n; i++) {
            out.writeIntArray(mDynamicAudioBufferCapabilities[i]);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the dynamic audio default buffer time
     *
     * @param codec Audio codec
     * @return default buffer time
     */
    @UnsupportedAppUsage
    public int getDynamicAudioDefaultBufferTime(int codec) {
        return mDynamicAudioBufferCapabilities[codec][BUFFER_TYPE_DEFAULT_VALUE];
    }

    /**
     * Get the dynamic audio maximum buffer time
     *
     * @param codec Audio codec
     * @return maximum buffer time
     */
    @UnsupportedAppUsage
    public int getDynamicAudioMaximumBufferTime(int codec) {
        return mDynamicAudioBufferCapabilities[codec][BUFFER_TYPE_MAXIMUM_VALUE];
    }

    /**
     * Get the dynamic audio minimum buffer time
     *
     * @param codec Audio codec
     * @return minimum buffer time
     */
    @UnsupportedAppUsage
    public int getDynamicAudioMinimumBufferTime(int codec) {
        return mDynamicAudioBufferCapabilities[codec][BUFFER_TYPE_MINIMUM_VALUE];
    }

    @UnsupportedAppUsage
    public int getDynamicAudioBufferCodecNumber() {
        return mDynamicAudioBufferCodecNumber;
    }

    private static String codecTypeToString(int type) {
        switch (type) {
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC:
                return "CODEC_TYPE_SBC";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC:
                return "CODEC_TYPE_AAC";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX:
                return "CODEC_TYPE_APTX";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD:
                return "CODEC_TYPE_APTX_HD";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC:
                return "CODEC_TYPE_LDAC";
            default:
                return "CODEC_TYPE_UNKNOWN:" + type;
        }
    }
}
