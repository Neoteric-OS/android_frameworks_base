/*
 * Copyright 2020 The Android Open Source Project
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
import android.icu.util.Calendar;

import java.security.cert.X509Certificate;

/**
 * A class that supports querying the capabilities of a {@link IdentityCredentialStore} as
 * implemented in secure hardware.
 *
 * <p>Capabilities depend on the Android system features and can be queried using
 * {@link android.content.pm#getSystemAvailableFeatures} and
 * {@link android.content.pm#hasSystemFeature(String, int)}.
 * The feature names in question are
 * {@link android.content.pm#FEATURE_IDENTITY_CREDENTIAL_HARDWARE} for the normal store and
 * {@link android.content.pm#FEATURE_IDENTITY_CREDENTIAL_HARDWARE_DIRECT_ACCESS}
 * for the direct access store.
 *
 * <p>Known feature versions include:
 * <ul>
 * <li>202009: This feature corresponds to the features included in the Identity Credential API
 * in Android 11.</li>
 * <li>202101: This feature version corresponds to the features included in the Identity
 * Credential API in Android 12. It adds support for {@link IdentityCredential#delete(byte[])},
 * {@link IdentityCredential#update(PersonalizationData)},
 * {@link IdentityCredential#proveOwnership(byte[])},
 * {@link IdentityCredential#storeStaticAuthenticationData(X509Certificate, Calendar, byte[])}.</li>
 * </ul>
 */
public class IdentityCredentialStoreCapabilities {
    IdentityCredentialStoreCapabilities() {}

    /**
     * Returns whether the credential store is for direct access.
     *
     * @return {@code true} if credential store is for direct access, {@code false} if not.
     */
    public boolean isDirectAccess() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the feature version of the {@link IdentityCredentialStore}.
     *
     * @return the feature version.
     */
    public int getFeatureVersion() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets a list of supported document types.
     *
     * <p>Only the direct-access store may restrict the kind of document types that can be used for
     * credentials. The default store always supports any document type.
     *
     * @return The supported document types or the empty array if any document type is supported.
     */
    public @NonNull String[] getSupportedDocTypes() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns whether {@link IdentityCredential#delete(byte[])} is supported
     * by the underlying hardware.
     *
     * <p>This is supported in feature version 202101 and later.
     *
     * @return {@code true} if supported, {@code false} if not.
     */
    public boolean isDeleteCredentialSupported() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if {@link IdentityCredential#update(PersonalizationData)} is supported
     * by the underlying hardware.
     *
     * <p>This is supported in feature version 202101 and later.
     *
     * @return {@code true} if supported, {@code false} if not.
     */
    public boolean isUpdateCredentialSupported() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if {@link IdentityCredential#proveOwnership(byte[])} is supported by the
     * underlying hardware.
     *
     * <p>This is supported in feature version 202101 and later.
     *
     * @return {@code true} if supported, {@code false} if not.
     */
    public boolean isProveOwnershipSupported() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if
     * {@link IdentityCredential#storeStaticAuthenticationData(X509Certificate, Calendar, byte[])}
     * is supported by the underlying hardware.
     *
     * <p>This is supported in feature version 202101 and later.
     *
     * @return {@code true} if supported, {@code false} if not.
     */
    public boolean isStaticAuthenticationDataExpirationSupported() {
        throw new UnsupportedOperationException();
    }

}
