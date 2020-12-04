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

package android.security;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import android.annotation.NonNull;
import android.hardware.security.keymint.HardwareAuthToken;
import android.hardware.security.keymint.Timestamp;

import java.nio.ByteOrder;

/**
 * @hide This Utils class provides method(s) for AuthToken conversion.
 * (Most of the code here is recycled from 12790205)
 */
public class AuthTokenUtils {

    private AuthTokenUtils(){
    }

    /**
     * Build a HardwareAuthToken from a byte array
     * @param array byte array representing an auth token
     * @return HardwareAuthToken representation of an auth token
     */
    public static @NonNull HardwareAuthToken toHardwareAuthToken(@NonNull byte[] array) {
        final HardwareAuthToken hardwareAuthToken = new HardwareAuthToken();

        // First byte is version, which doesn't not exist in HardwareAuthToken anymore
        // Next 8 bytes is the challenge.
        hardwareAuthToken.challenge = getLong(array, 1 /* offset */);

        // Next 8 bytes is the userId
        hardwareAuthToken.userId = getLong(array, 9 /* offset */);

        // Next 8 bytes is the authenticatorId.
        hardwareAuthToken.authenticatorId = getLong(array, 17 /* offset */);

        // Next 4 bytes is the authenticatorType.
        hardwareAuthToken.authenticatorType = flipIfNativelyLittle(getInt(array, 25 /* offset */));

        // Next 8 bytes is the timestamp.
        final Timestamp timestamp = new Timestamp();
        timestamp.milliSeconds = flipIfNativelyLittle(getLong(array, 29 /* offset */));
        hardwareAuthToken.timestamp = timestamp;

        // Last 32 bytes is the mac, 37:69
        hardwareAuthToken.mac = new byte[32];
        System.arraycopy(array, 37 /* srcPos */,
                hardwareAuthToken.mac,
                0 /* destPos */,
                32 /* length */);

        return hardwareAuthToken;
    }

    private static long getLong(byte[] array, int offset) {
        long result = 0;
        // Lowest bit is LSB
        for (int i = 0; i < 8; i++) {
            result += (long) ((array[i + offset] & 0xffL) << (8 * i));
        }
        return result;
    }

    private static long flipIfNativelyLittle(long l) {
        if (LITTLE_ENDIAN == ByteOrder.nativeOrder()) {
            return Long.reverseBytes(l);
        }
        return l;
    }

    private static int flipIfNativelyLittle(int i) {
        if (LITTLE_ENDIAN == ByteOrder.nativeOrder()) {
            return Integer.reverseBytes(i);
        }
        return i;
    }

    private static int getInt(byte[] array, int offset) {
        int result = 0;
        // Lowest bit is LSB
        for (int i = 0; i < 4; i++) {
            result += (int) (((int) array[i + offset] & 0xff) << (8 * i));
        }
        return result;
    }
}
