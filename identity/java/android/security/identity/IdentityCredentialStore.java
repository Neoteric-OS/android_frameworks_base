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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.ServiceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An interface to a secure store for user identity documents.
 *
 * <p>This interface is deliberately fairly general and abstract.  To the extent possible,
 * specification of the message formats and semantics of communication with credential
 * verification devices and issuing authorities (IAs) is out of scope. It provides the
 * interface with secure storage but a credential-specific Android application will be
 * required to implement the presentation and verification protocols and processes
 * appropriate for the specific credential type.
 *
 * <p>TODO: mention various backends, e.g. software emulation vs. calling into credstore/IC HAL
 * if on R.</p>
 *
 * <p>Multiple credentials can be created.  Each credential comprises:</p>
 * <ul>
 * <li>A document type, which is a string.</li>
 *
 * <li>A set of namespaces, which serve to disambiguate value names. It is recommended
 * that namespaces be structured as reverse domain names so that IANA effectively serves
 * as the namespace registrar.</li>
 *
 * <li>For each namespace, a set of name/value pairs, each with an associated set of
 * access control profile IDs.  Names are and values are typed and are either signed
 * integers, booleans, UTF-8 strings or bytestrings.</li>
 *
 * <li>A set of access control profiles, each with a profile ID and a specification
 * of the conditions which satisfy the profile's requirements.</li>
 *
 * <li>An asymmetric key pair which is used to authenticate the credential to the IA,
 * called the <em>CredentialKey</em>.</li>
 *
 * <li>A set of zero or more named reader authentication public keys, which are used to authenticate
 * an authorized reader to the credential.</li>
 *
 * <li>A set of named signing keys, which are used to sign collections of values and session
 * transcripts.</li>
 * </ul>
 */
public class IdentityCredentialStore {

    /**
     * Specifies that the cipher suite that will be used to secure communications between the reader
     * is:
     *
     * <ul>
     * <li>ECDHE with HKDF-SHA-256 for key agreement.</li>
     * <li>AES-256 with GCM block mode for authenticated encryption (nonces are incremented by one
     * for every message).</li>
     * <li>ECDSA with SHA-256 for signing (used for signing session transcripts to defeat
     * man-in-the-middle attacks), signing keys are not ephemeral. See {@link IdentityCredential}
     * for details on reader and prover signing keys.</li>
     * </ul>
     *
     * <p>
     * At present this is the only supported cipher suite.
     */
    public static final int CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256 = 1;
    private static final String TAG = "IdentityCredentialStore";

    private Context mContext = null;
    private ICredentialStore mStore = null;

    private IdentityCredentialStore(@NonNull Context context, ICredentialStore store) {
        mContext = context;
        mStore = store;
    }

    static IdentityCredentialStore getInstanceForType(@NonNull Context context,
            int credentialStoreType) {
        ICredentialStoreFactory storeFactory =
                ICredentialStoreFactory.Stub.asInterface(
                    ServiceManager.getService("android.security.identity"));

        ICredentialStore credStore = null;
        try {
            credStore = storeFactory.getCredentialStore(credentialStoreType);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            if (e.errorCode == ICredentialStore.ERROR_GENERIC) {
                return null;
            } else {
                throw new RuntimeException("Unexpected ServiceSpecificException with code "
                        + e.errorCode, e);
            }
        }
        if (credStore == null) {
            return null;
        }

        return new IdentityCredentialStore(context, credStore);
    }

    private static IdentityCredentialStore sInstanceDefault = null;
    private static IdentityCredentialStore sInstanceDirectAccess = null;

    /**
     * Gets the default {@link IdentityCredentialStore}.
     *
     * @param context the application context.
     * @return the {@link IdentityCredentialStore}.
     */
    public static @NonNull IdentityCredentialStore getInstance(@NonNull Context context) {
        if (sInstanceDefault == null) {
            sInstanceDefault = getInstanceForType(context,
                    ICredentialStoreFactory.CREDENTIAL_STORE_TYPE_DEFAULT);
        }
        return sInstanceDefault;
    }

    /**
     * Gets the {@link IdentityCredentialStore} for direct access.
     *
     * Direct access requires specialized NFC hardware and may not be supported on all devices.
     *
     * @param context the application context.
     * @return the {@link IdentityCredentialStore} or null if direct access is not supported
     *     on this device.
     */
    public static @Nullable IdentityCredentialStore getDirectAccessInstance(@NonNull
            Context context) {
        if (sInstanceDirectAccess == null) {
            sInstanceDirectAccess = getInstanceForType(context,
                    ICredentialStoreFactory.CREDENTIAL_STORE_TYPE_DIRECT_ACCESS);
        }
        return sInstanceDirectAccess;
    }

    /**
     * Creates a new credential.
     *
     * @param credentialName The name used to identify the credential.
     * @param docType        The document type for the credential.
     * @return A @{link WritableIdentityCredential} that can be used to create a new credential.
     * @throws AlreadyPersonalizedException if a credential with the given name already exists.
     */
    public @NonNull WritableIdentityCredential createCredential(@NonNull String credentialName,
            @NonNull String docType) throws AlreadyPersonalizedException {
        try {
            IWritableCredential wc;
            wc = mStore.createCredential(credentialName, docType);
            return new CredstoreWritableIdentityCredential(mContext, credentialName, docType, wc);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            if (e.errorCode == ICredentialStore.ERROR_ALREADY_PERSONALIZED) {
                throw new AlreadyPersonalizedException(e.getMessage(), e);
            } else {
                throw new RuntimeException("Unexpected ServiceSpecificException with code "
                        + e.errorCode, e);
            }
        }
    }

    /**
     * Retrieve a named credential.
     *
     * @param credentialName the name of the credential to retrieve.
     * @param cipherSuite    the cipher suite to use for communicating with the verifier.
     * @return The named credential, or null if not found.
     */
    public @Nullable IdentityCredential getCredentialByName(@NonNull String credentialName,
                                                            @Ciphersuite int cipherSuite)
            throws CipherSuiteNotSupportedException {
        try {
            ICredential credstoreCredential;
            credstoreCredential = mStore.getCredentialByName(credentialName, cipherSuite);
            return new CredstoreIdentityCredential(mContext, credentialName, cipherSuite,
                    credstoreCredential);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            if (e.errorCode == ICredentialStore.ERROR_CIPHER_SUITE_NOT_SUPPORTED) {
                throw new CipherSuiteNotSupportedException(e.getMessage(), e);
            } else {
                throw new RuntimeException("Unexpected ServiceSpecificException with code "
                        + e.errorCode, e);
            }
        }
    }

    /**
     * Delete a named credential.
     *
     * <p>This method returns a <a href="https://tools.ietf.org/html/rfc7049">CBOR</a>
     * data structure with the document type, signed by the CredentialKey. The CBOR data structure
     * is defined by {@code ProofOfDeletion} in the following
     * <a href="https://tools.ietf.org/html/draft-ietf-cbor-cddl-06">CDDL</a> schema:</p>
     *
     * <pre>
     *     ProofOfDeletion = [
     *         DeletionSignedData,
     *         bstr                       ; ECDSA signature over SignedData
     *     ]
     *
     *     DeletionSignedData = [
     *          "ProofOfDeletion",            ; tstr
     *          tstr,                         ; DocType
     *          bool                          ; true if this is a test credential, should
     *                                        ; always be false.
     *      ]
     * </pre>
     *
     * @param credentialName the name of the credential to delete.
     * @return {@code null} if the credential was not found, the CBOR data structure above
     * if the credential was found and deleted.
     * @throws IdentityCredentialException if an error occurred.
     */
    public @Nullable byte[] deleteCredentialByName(@NonNull String credentialName)
            throws IdentityCredentialException {
        ICredential credstoreCredential = null;
        try {
            try {
                credstoreCredential = mStore.getCredentialByName(credentialName,
                        CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            } catch (android.os.ServiceSpecificException e) {
                if (e.errorCode == ICredentialStore.ERROR_NO_SUCH_CREDENTIAL) {
                    return null;
                }
            }
            byte[] proofOfDeletion = credstoreCredential.deleteCredential();
            return proofOfDeletion;
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unknown Remote Exception ", e);
        }
    }

    /** @hide */
    @IntDef(value = {CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Ciphersuite {
    }

}
