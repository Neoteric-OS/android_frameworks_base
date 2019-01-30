/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.captiveportallogin;

import android.net.http.SslCertificate;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Utilities for working with SSL certificates.
 */
public final class SslCertificateUtil {
    private static String fingerprint(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if (i + 1 != bytes.length) {
                sb.append(':');
            }
        }
        return sb.toString();
    }

    /**
     * Get a formatted hex string for the serial number of the X509Certificate of a SslCertificate.
     */
    public static String formatX509SerialNumber(SslCertificate cert) {
        if (cert.getX509Certificate() == null) {
            return "";
        }
        final BigInteger serialNumber = cert.getX509Certificate().getSerialNumber();
        if (serialNumber == null) {
            return "";
        }

        return fingerprint(serialNumber.toByteArray());
    }

    /**
     * Get a hex formatted digest string for the X509Certificate of a SslCertificate.
     */
    public static String getX509Digest(SslCertificate cert, String algorithm) {
        final X509Certificate x509Certificate = cert.getX509Certificate();
        if (x509Certificate == null) {
            return "";
        }
        try {
            final byte[] bytes = x509Certificate.getEncoded();
            final MessageDigest md = MessageDigest.getInstance(algorithm);
            final byte[] digest = md.digest(bytes);
            return fingerprint(digest);
        } catch (CertificateEncodingException | NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    private SslCertificateUtil() {}
}
