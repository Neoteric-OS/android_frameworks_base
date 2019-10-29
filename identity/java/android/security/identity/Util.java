/*
 * Copyright 2019 The Android Open Source Project
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

package android.security.identity;

import android.annotation.NonNull;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.util.Log;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class Util {
    private static final String TAG = "Util";

    // To avoid pulling something like cbor-java into the Android Framework,
    // we implement our own simple minimalistic CBOR encoder and decoder.
    //
    // See https://tools.ietf.org/html/rfc7049 for CBOR encoding.

    private static final int CBOR_TYPE_UINT = 0;
    private static final int CBOR_TYPE_NINT = 1;
    private static final int CBOR_TYPE_BYTE_STRING = 2;
    private static final int CBOR_TYPE_UTF8_STRING = 3;
    private static final int CBOR_TYPE_SEMANTIC = 6;
    private static final int CBOR_TYPE_SIMPLE = 7;

    private static final int CBOR_SIMPLE_VALUE_FALSE = 20;
    private static final int CBOR_SIMPLE_VALUE_TRUE = 21;

    static final int CBOR_SEMANTIC_TAG_DATETIME = 0;

    static void cborWriteLength(ByteArrayOutputStream baos, int type, long value)
            throws IOException {
        if (value < 24) {
            baos.write((type << 5) | ((int) value));
        } else if (value < 0x100) {
            baos.write((type << 5) | 24);
            baos.write((int) value);
        } else if (value < 0x10000) {
            baos.write((type << 5) | 25);
            baos.write((int) ((value >> 8) & 0xff));
            baos.write((int) (value & 0xff));
        } else if (value < 0x100000000L) {
            baos.write((type << 5) | 26);
            baos.write((int) ((value >> 24) & 0xff));
            baos.write((int) ((value >> 16) & 0xff));
            baos.write((int) ((value >> 8) & 0xff));
            baos.write((int) (value & 0xff));
        } else {
            baos.write((type << 5) | 27);
            baos.write((int) ((value >> 56) & 0xff));
            baos.write((int) ((value >> 48) & 0xff));
            baos.write((int) ((value >> 40) & 0xff));
            baos.write((int) ((value >> 32) & 0xff));
            baos.write((int) ((value >> 24) & 0xff));
            baos.write((int) ((value >> 16) & 0xff));
            baos.write((int) ((value >> 8) & 0xff));
            baos.write((int) (value & 0xff));
        }
    }

    static byte[] cborEncodeBoolean(boolean value) {
        int simpleValue = value ? CBOR_SIMPLE_VALUE_TRUE : CBOR_SIMPLE_VALUE_FALSE;
        return new byte[] {(byte) ((CBOR_TYPE_SIMPLE << 5) | simpleValue)};
    }

    static byte[] cborEncodeString(@NonNull String value) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] valueBytes = value.getBytes("UTF-8");
            cborWriteLength(baos, CBOR_TYPE_UTF8_STRING, valueBytes.length);
            baos.write(valueBytes);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }
    }

    static byte[] cborEncodeLong(long value) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (value >= 0) {
                cborWriteLength(baos, CBOR_TYPE_UINT, value);
            } else {
                cborWriteLength(baos, CBOR_TYPE_NINT, -value - 1);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }
    }

    static byte[] cborEncodeBytestring(@NonNull byte[] value) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cborWriteLength(baos, CBOR_TYPE_BYTE_STRING, value.length);
            baos.write(value);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }
    }

    static byte[] cborEncodeCalendar(@NonNull Calendar calendar) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ");
        if (calendar.isSet(Calendar.MILLISECOND) && calendar.get(Calendar.MILLISECOND) != 0) {
            df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ");
        }
        df.setTimeZone(calendar.getTimeZone());
        Date val = calendar.getTime();
        String dateString = df.format(val);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write((CBOR_TYPE_SEMANTIC << 5) | CBOR_SEMANTIC_TAG_DATETIME);
            byte[] valueBytes = dateString.getBytes("UTF-8");
            cborWriteLength(baos, CBOR_TYPE_UTF8_STRING, valueBytes.length);
            baos.write(valueBytes);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }
    }

    static boolean cborDecodeBoolean(@NonNull byte[] data) throws IdentityCredentialException {
        if (data.length == 1) {
            if (data[0] == (byte) ((CBOR_TYPE_SIMPLE << 5) | CBOR_SIMPLE_VALUE_TRUE)) {
                return true;
            } else if (data[0] == (byte) ((CBOR_TYPE_SIMPLE << 5) | CBOR_SIMPLE_VALUE_FALSE)) {
                return false;
            }
        }
        throw new IdentityCredentialException("Data is not a bool");
    }

    static int cborGetMajorType(@NonNull byte[] data) throws IdentityCredentialException {
        if (data.length <= 1) {
            throw new IdentityCredentialException("Data is empty");
        }
        return (data[0] & 0xff) >> 5;
    }

    static Pair<Long, Integer> cborGetNumBytes(@NonNull byte[] data)
            throws IdentityCredentialException {
        if (data.length <= 1) {
            throw new IdentityCredentialException("Data is empty");
        }
        int low = data[0] & 0x1f;
        if (low < 24) {
            return new Pair<Long, Integer>((long) low, 0);
        } else if (low < 28) {
            int numBytes = 1 << (low - 24);
            if (data.length < numBytes + 1) {
                throw new IdentityCredentialException("Data is too short");
            }
            long value = 0;
            for (int n = 0; n < numBytes; n++) {
                value <<= 8;
                value |= data[n + 1] & 0xff;
            }
            return new Pair<Long, Integer>(value, numBytes);
        } else if (low == 31) {
            throw new IdentityCredentialException("Indefinite-length not supported");
        } else {
            throw new IdentityCredentialException("Unexpected value in length field");
        }
    }

    static String cborDecodeString(@NonNull byte[] data) throws IdentityCredentialException {
        if (cborGetMajorType(data) != CBOR_TYPE_UTF8_STRING) {
            throw new IdentityCredentialException("Data is not a UTF-8 string");
        }
        Pair<Long, Integer> numBytesAndSkip = cborGetNumBytes(data);
        byte[] utf8 = Arrays.copyOfRange(data, 1 + numBytesAndSkip.second, data.length);
        try {
            return new String(utf8, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IdentityCredentialException("UTF-8 not supported", e);
        }
    }

    static long cborDecodeLong(@NonNull byte[] data) throws IdentityCredentialException {
        int majorType = cborGetMajorType(data);
        if (majorType == CBOR_TYPE_UINT) {
            Pair<Long, Integer> numBytesAndSkip = cborGetNumBytes(data);
            return numBytesAndSkip.first;
        } else if (majorType == CBOR_TYPE_NINT) {
            Pair<Long, Integer> numBytesAndSkip = cborGetNumBytes(data);
            return -numBytesAndSkip.first - 1;
        } else {
            throw new IdentityCredentialException("Data is not an UINT or NINT");
        }
    }

    static byte[] cborDecodeBytestring(@NonNull byte[] data) throws IdentityCredentialException {
        if (cborGetMajorType(data) != CBOR_TYPE_BYTE_STRING) {
            throw new IdentityCredentialException("Data is not a bytestring");
        }
        Pair<Long, Integer> numBytesAndSkip = cborGetNumBytes(data);
        return Arrays.copyOfRange(data, 1 + numBytesAndSkip.second, data.length);
    }

    static Calendar cborDecodeCalendar(@NonNull byte[] data) throws IdentityCredentialException {
        if (cborGetMajorType(data) != CBOR_TYPE_SEMANTIC) {
            Log.e(TAG, "Major type = " + cborGetMajorType(data));
            throw new IdentityCredentialException("Data is not a tag");
        }
        int tagValue = ((int) data[0]) & 0x1f;
        if (tagValue != CBOR_SEMANTIC_TAG_DATETIME) {
            throw new IdentityCredentialException("Tag value is not DATETIME");
        }
        byte[] childData = Arrays.copyOfRange(data, 1, data.length);
        String dateString = cborDecodeString(childData);

        TimeZone parsedTz = TimeZone.getTimeZone("UTC");
        if (!dateString.endsWith("Z")) {
            String timeZoneSubstr = dateString.substring(dateString.length() - 6);
            parsedTz = TimeZone.getTimeZone("GMT" + timeZoneSubstr);
        }

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        df.setTimeZone(parsedTz);
        Date date = null;
        try {
            date = df.parse(dateString);
        } catch (ParseException e) {
            // Try again, this time without the milliseconds
            df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            df.setTimeZone(parsedTz);
            try {
                date = df.parse(dateString);
            } catch (ParseException e2) {
                throw new IdentityCredentialException("Error parsing string", e2);
            }
        }

        Calendar c = new GregorianCalendar();
        c.clear();
        c.setTimeZone(df.getTimeZone());
        c.setTime(date);
        return c;
    }

    static int[] integerCollectionToArray(Collection<Integer> collection) {
        int[] result = new int[collection.size()];
        int n = 0;
        for (int item : collection) {
            result[n++] = item;
        }
        return result;
    }

    /**
     * Computes an HKDF.
     *
     * This is based on https://github.com/google/tink/blob/master/java/src/main/java/com/google
     * /crypto/tink/subtle/Hkdf.java
     * which is also Copyright (c) Google and also licensed under the Apache 2 license.
     *
     * @param macAlgorithm the MAC algorithm used for computing the Hkdf. I.e., "HMACSHA1" or
     *                     "HMACSHA256".
     * @param ikm          the input keying material.
     * @param salt         optional salt. A possibly non-secret random value. If no salt is
     *                     provided (i.e. if
     *                     salt has length 0) then an array of 0s of the same size as the hash
     *                     digest is used as salt.
     * @param info         optional context and application specific information.
     * @param size         The length of the generated pseudorandom string in bytes. The maximal
     *                     size is
     *                     255.DigestSize, where DigestSize is the size of the underlying HMAC.
     * @return size pseudorandom bytes.
     */
    static byte[] computeHkdf(
            String macAlgorithm, final byte[] ikm, final byte[] salt, final byte[] info, int size) {
        Mac mac = null;
        try {
            mac = Mac.getInstance(macAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such algorithm: " + macAlgorithm, e);
        }
        if (size > 255 * mac.getMacLength()) {
            throw new RuntimeException("size too large");
        }
        try {
            if (salt == null || salt.length == 0) {
                // According to RFC 5869, Section 2.2 the salt is optional. If no salt is provided
                // then HKDF uses a salt that is an array of zeros of the same length as the hash
                // digest.
                mac.init(new SecretKeySpec(new byte[mac.getMacLength()], macAlgorithm));
            } else {
                mac.init(new SecretKeySpec(salt, macAlgorithm));
            }
            byte[] prk = mac.doFinal(ikm);
            byte[] result = new byte[size];
            int ctr = 1;
            int pos = 0;
            mac.init(new SecretKeySpec(prk, macAlgorithm));
            byte[] digest = new byte[0];
            while (true) {
                mac.update(digest);
                mac.update(info);
                mac.update((byte) ctr);
                digest = mac.doFinal();
                if (pos + digest.length < size) {
                    System.arraycopy(digest, 0, result, pos, digest.length);
                    pos += digest.length;
                    ctr++;
                } else {
                    System.arraycopy(digest, 0, result, pos, size - pos);
                    break;
                }
            }
            return result;
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Error MACing", e);
        }
    }

}
