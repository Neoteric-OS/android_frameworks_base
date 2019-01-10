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
import java.security.cert.X509Certificate;
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
     * 
     * @throws AlreadyPersonalizedException
     *             if this credential has already been personalized.
     * 
     * @throws IdentityCredentialException
     *             if unable to communicate with secure hardware.
     */
    public @NonNull X509Certificate[] getCredentialKeyCertificateChain(@NonNull byte[] challenge)
            throws IdentityCredentialException {
        return null;
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
     *         is is defined by "ProofOfProvisioning" in the following
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
     * @throws AlreadyPersonalizedException
     *             if this credential has already been personalized.
     * 
     * @throws IdentityCredentialException
     *             if unable to communicate with secure hardware.
     */
    public byte[] personalize(Collection<AccessControlProfile> accessControlProfiles,
            Collection<EntryNamespace> entryNamespaces) throws IdentityCredentialException {
        return null;
    }
}
