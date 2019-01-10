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

package android.security.identity;

import android.annotation.NonNull;
import android.os.RemoteException;
import android.security.identity.credstore.IWritableCredential;
import android.security.identity.credstore.ResultCode;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;

/**
 * An <code>IdentityCredential</code> that can be provisioned.
 *
 * Identity Credentials, once fully provisioned, are immutable. To update an Identity Credential it
 * is necessary to delete (See {@link IdentityCredential#delete} the old credential and create and
 * provision a new one.
 */
@SuppressWarnings("unused") // TODO(swillden): Remove this
public class WritableIdentityCredential {
    private static final String TAG = "WritableIdentityCredential";
    private static final int INITIAL_CAPACITY = 1000;

    private IWritableCredential mBinder;

    /**
     * @hide
     */
    WritableIdentityCredential(IWritableCredential binder) {
        mBinder = binder;
    }

    /**
     * Generates and returns an X.509 certificate chain for the CredentialKey which identifies this
     * credential to the issuing authority. The certificate contains a Keystore attestation
     * extension which describes the key and the security hardware in which it lives.
     *
     * It is not strictly necessary to use this method to provision a credential if the issuing
     * authority doesn't care about the nature of the security hardware. If called, however, this
     * method must be called before {@link #personalize}.
     *
     * @param challenge
     *            is a byte array whose contents should be unique, fresh and provided by the issuing
     *            authority. The value provided is embedded in the attestation extension and enables
     *            the issuing authority to verify that the attestation certificate is fresh.
     *
     * @return the certificate for this credential's CredentialKey.
     */
    public @NonNull Collection<X509Certificate> getCredentialKeyCertificateChain(
            @NonNull byte[] challenge) throws IdentityCredentialException {
        try {
            ResultCode result = new ResultCode();
            byte[] out = mBinder.getCredentialKeyCertificateChain(challenge, result);
            if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                //
            }
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (Collection<X509Certificate>) certFactory.generateCertificates(
                            new ByteArrayInputStream(out));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Stores all of the data in the credential, with the specified access control profiles.
     *
     * @param accessControlProfiles
     *            The collection of access control profiles that are used to secure the data. Each
     *            profile has a name, and each data item can specify any number of profile names.
     *
     * @param entryNamespaces
     *            The data to be provisioned, grouped into namespaces. See {@link EntryNamespace}.
     *
     * @return A <a href="https://tools.ietf.org/html/rfc7049">CBOR</a> data structure containing
     *         all of the data items stored and signed by the CredentialKey. The CBOR data structure
     *         is defined by "ProofOfProvisioning" in the following
     *         <a href="https://tools.ietf.org/html/draft-ietf-cbor-cddl-06">CDDL</a> schema:
     *
     *         <pre>
     *             ProofOfProvisioning = [
     *                 SignedData,
     *                 bstr                           ; ECDSA signature over SignedData
     *             ]
     *
     *             SignedData = [
     *                  tstr,                         ; DocType
     *                  [ * AccessControlProfile ],
     *                  Data,
     *                  bool                          ; true if this is a test credential, should
     *                                                ; always be false.
     *              ]
     *
     *              AccessControlProfile = {
     *                  "id": uint,
     *                  ? "readerAuthPubKey" : bstr,
     *                  ? (
     *                      "capabilityType": uint
     *                       ? "timeout": uint,
     *                  )
     *              }
     *
     *              Data = {
     *                  * Namespace =&gt; [ + Entry ]
     *              },
     *
     *              Namespace = tstr
     *
     *              Entry = {
     *                  "name" : tstr,
     *                  "accessControlProfiles" : [ * uint ],
     *                  "value" : bstr / tstr / int / bool,
     *                  "directlyAvailable" : bool
     *              }
     *         </pre>
     *
     * throws AlreadyPersonalizedException
     *             if this credential has already been personalized.
     *
     * throws IdentityCredentialException
     *             if unable to communicate with secure hardware.
     */
    public byte[] personalize(Collection<AccessControlProfile> accessControlProfiles,
            Collection<EntryNamespace> entryNamespaces) throws IdentityCredentialException {
        if (accessControlProfiles.isEmpty()) {
            // Error
        }
        if (entryNamespaces.isEmpty()) {
            // Error
        }
        try {
            ByteArrayOutputStream cborData = new ByteArrayOutputStream(INITIAL_CAPACITY);
            ResultCode result = new ResultCode();
            cborData.write(mBinder.startPersonalization((byte) accessControlProfiles.size(),
                                                        entryNamespaces.size(),
                                                        result));
            if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                // Error
            }
            for (AccessControlProfile profile : accessControlProfiles) {
                // TODO(jbires): ask swillden@ about AccessControlProfile.aidl composition
                cborData.write(mBinder.addAccessControlProfile(profile.toParcel(), result));
                if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                    // Error
                }
            }
            for (EntryNamespace entryNamespace : entryNamespaces) {
                cborData.write(mBinder.addNamespace(entryNamespace.getNamespaceName(),
                                                (char) entryNamespace.getEntryNames().size(),
                                                result));
                if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                    // Error
                }
                for (String entry : entryNamespace.getEntryNames()) {
                    Object entryValue = entryNamespace.getEntryValue(entry);
                    Byte[] temp =
                            entryNamespace.getAccessControlProfiles(entry).toArray(new Byte[0]);
                    byte[] accessControlProfileIds = new byte[temp.length];
                    for (int i = 0; i < temp.length; i++) {
                        accessControlProfileIds[i] = temp[i];
                    }
                    if (entryValue instanceof Boolean) {
                        cborData.write(mBinder.addBoolEntry(
                                    accessControlProfileIds,
                                    entry,
                                    (boolean) entryValue,
                                    entryNamespace.isDirectlyAccessible(entry),
                                    result));
                        if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                            //Error
                        }
                    } else if (entryValue instanceof Long) {
                        cborData.write(mBinder.addIntegerEntry(
                                        accessControlProfileIds,
                                        entry,
                                        (long) entryValue,
                                        entryNamespace.isDirectlyAccessible(entry),
                                        result));
                        if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                            // Error
                        }
                    } else if (entryValue instanceof String) {
                        byte[] entryByteArray =
                                        ((String) entryValue).getBytes(StandardCharsets.UTF_8);
                        int chunkSize = mBinder.chunkSize();
                        int index = 0;
                        if (entryByteArray.length <= chunkSize) {
                            chunkSize = entryByteArray.length;
                        }
                        cborData.write(mBinder.addChunkableEntry(
                                            accessControlProfileIds,
                                            entry,
                                            0, // add int type here
                                            Arrays.copyOfRange(entryByteArray,
                                                               index,
                                                               index + chunkSize),
                                            entryByteArray.length,
                                            entryNamespace.isDirectlyAccessible(entry),
                                            result));
                        if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                            // Error
                        }
                        index += chunkSize;
                        while (index < entryByteArray.length) {
                            if (entryByteArray.length - index < chunkSize) {
                                chunkSize = entryByteArray.length - index;
                            }
                            cborData.write(mBinder.addChunk(
                                                Arrays.copyOfRange(entryByteArray,
                                                                   index,
                                                                   index + chunkSize),
                                                result));
                            if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                                // Error
                            }
                            index += chunkSize;
                        }
                    } else if (entryValue instanceof byte[]) {
                        byte[] entryByteArray = (byte[]) entryValue;
                        int chunkSize = mBinder.chunkSize();
                        int index = 0;
                        if (entryByteArray.length <= chunkSize) {
                            chunkSize = entryByteArray.length;
                        }
                        cborData.write(
                                mBinder.addChunkableEntry(
                                    accessControlProfileIds,
                                    entry,
                                    0, // add int type here
                                    Arrays.copyOfRange(entryByteArray, index, index + chunkSize),
                                    entryByteArray.length,
                                    entryNamespace.isDirectlyAccessible(entry),
                                    result));
                        if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                            // Error
                        }
                        index += chunkSize;
                        while (index < entryByteArray.length) {
                            if (entryByteArray.length - index < chunkSize) {
                                chunkSize = entryByteArray.length - index;
                            }
                            cborData.write(mBinder.addChunk(
                                                Arrays.copyOfRange(
                                                        entryByteArray, index, index + chunkSize),
                                                result));
                            if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                                // Error
                            }
                            index += chunkSize;
                        }
                    } else {
                        // Something is wrong
                    }
                }
            }
            cborData.write(mBinder.finishAddingEntries(result));
            if (result.errorCode != IdentityCredentialStore.RETURN_CODE_OK) {
                // Error
            }
            return cborData.toByteArray();
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        } catch (IOException e) {
            Log.w(TAG, "Issue writing bytes", e);
            throw new AssertionError(e);
        }
    }
}
