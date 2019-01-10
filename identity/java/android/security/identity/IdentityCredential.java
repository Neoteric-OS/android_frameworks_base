/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.security.identity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Pair;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

/**
 * Represents a provisioned, usable <code>IdentityCredential</code>. Used to retrieve credential
 * data items for presentation, and to manage signing keys and static authentication data.
 *
 * <h1>Retrieving credential data</h1>
 *
 * <p>
 * <code>IdentityCredential</code>s use a protocol based on exchange of ephemeral public keys to
 * produce a symmetric session key used to provide authenticated encryption on messages. The
 * exchanged ephemeral public keys and all other exchanged data relevant to the establishment of the
 * session key are collectively called the "session transcript". This protocol is used by the
 * <a href="https://www.iso.org/standard/69084.html">ISO 18013-5</a> standard for mobile driving
 * licenses, and is the basis for ongoing ISO work on other standardized identity credentials.
 *
 * <p>
 * To retrieve data from an <code>IdentityCredential</code> do the following:
 * <ol>
 * <li>Call {@link #createEphemeralKeyPair}. A private ECDH key will be generated and returned. This
 * key should be used with the reader public key to compute a shared session key to encrypt and
 * authenticate subsequent communications. The public key must also be sent to the reader so it can
 * perform the same session key derivation. It's important to use this function to create the
 * ephemeral key pair rather than creating it with the Java Crypto API because the secure
 * environment will expect to find the public portion in the session transcript (see below). If the
 * key pair is created some other way and the reader signs the session transcript.
 * <li>Call <code>getPublic().getEncoded()</code> on the ephemeral KeyPair to get the public key,
 * and send it to the reader over some communication channel.
 * <li>Receive the data request from the reader. If the previous steps were performed, this request
 * message should be encrypted using the session key and the ciphersuite selected when the
 * <code>IdentityCredential</code> was created. Use {@link #decryptMessageFromReader} to decrypt it.
 * <li>Parse the decrypted message to extract the data request, reader ephemeral public key and the
 * reader signature (if any; reader signing is optional, but is needed if reader authentication was
 * configured for any requested data elements). Message format is specified by the standard body
 * that defines the credential type, though to use with reader authentication, the data request must
 * be a <a href="https://tools.ietf.org/html/rfc7049">CBOR</a> map that conforms to the following
 * <a href="https://tools.ietf.org/html/draft-ietf-cbor-cddl-06">CDDL</a> schema: <br>
 * <br>
 *
 * <pre>
 *     SignedReqest = {
 *         "SessionTranscript" : any,
 *         "Request" : {
 *             ? "DocType" : tstr,
 *             + Namespace =&gt; DataItemNames
 *         }
 *     Namespace = tstr
 *     DataItemNames = [ + tstr ]
 * </pre>
 *
 * Also, the "SessionTranscript" entry of SignedRequest must be some CBOR structure that contains
 * within it a bytestring containing the ephemeral public key created with
 * {@link #createEphemeralKeyPair} and a bytestring containing the reader's ephemeral public key.
 * The security hardware will verify that the ephemeral public keys are present in the session
 * transcript, which proves that the signature is fresh. If the ephemeral public keys are not
 * present, an {@link InvalidRequestException} will be thrown.
 * <p>
 * The signature must be either null or a signature created by a reader key that was specified in
 * one of the {@link AccessControlProfile}s provisioned to the <code>IdentityCredential</code>
 * <li>Use {@link EntryNamespace.Builder} to populate each with the set of data entries the reader
 * requested. It is recommended to filter the request set according to user preferences, allowing
 * the user to control which data entries they wish to allow to be retrieved.
 * <li>Call {@link #getEntries getEntries} to retrieve the data and prover-generated authentication
 * code.
 * <li>Construct the reader response from the data in the {@link GetEntryResult} object. Note that
 * the <code>GetEntryResult</code> object contains the response data in two formats, the CBOR
 * structure returned by {@link GetEntryResult#getAuthenticatedData getAuthenticatedData()}, which
 * is the byte array authenticated, and the collection of {@link EntryNamespace} objects returned by
 * {@link GetEntryResult#getEntryNamespaces getEntryNamespaces()}.
 * <li>Use {@link #encryptMessageToReader} to encrypt the response, then send it to the reader.
 * </ol>
 *
 * <h1>Managing signing keys</h1>
 *
 * <p>
 * <code>IdentityCredential</code>s support two kinds of cryptographic data authentication, for two
 * different security goals:
 * <ul>
 * <li>Static authentication data created by the issuing authority, using keys that are never
 * present on a mobile device, and therefore can't be compromised by any attack on the device
 * security. These static authentication data can prove that the issuing authority asserts the
 * correctness of the data elements individually, and in combination. They prove the authenticity
 * and integrity of the credential, assuming the issuing authority maintains the security of their
 * keys.
 * <li>Dynamic authentication codes created by the <code>IdentityCredential</code>'s security
 * hardware. Because of the difficulty of extracting the private keys from the security hardware,
 * they prove that the credential has not been copied to another device (assuming the security
 * hardware is not compromised).
 * </ul>
 *
 * <p>
 * But unless care is taken these security goals comes at a privacy cost.
 * <code>IdentityCredential</code>s are designed to allow for selective presentation of data
 * elements, to increase user privacy. For example, a mobile driving license may have a data element
 * containing the user's age which allows the holder of the driving license to prove their age
 * without disclosing any other information about themselves: name, address, birthdate, etc. Only
 * the age and the photo are required. This is different from the current use of physical identity
 * cards, where users must provide their complete data set, often in electronic form (e.g. 2D
 * barcode). The static and dynamic digital signatures, however, potentially provide a
 * globally-unique identifier. So even though a reader may not get much personally-identifiable data
 * from the <code>IdentityCredential</code>, it can easily store information that allows it to
 * identify that the individual is the same one who presented data on prior occasions. This means
 * that if the same static authentication data and dynamic authentication keys are used for every
 * credential presentation, the credential is <em>linkable</em>, even when no unique data is
 * retrieved.
 *
 * <p>
 * <code>IdentityCredential</code> support optional unlinkability through the use of multiple sets
 * of static authentication data and dynamic authentication keys. The process is as follows:
 * <ol>
 * <li>Create several dynamic authentication key pairs using {@link #generateAuthenticationKeyPair}.
 * The name argument is an arbitrary label used later to reference the key pair. The returned value
 * is an X.509 certificate signed by the CredentialKey (see
 * {@link #getCredentialKeyCertificateChain}).
 * <li>For each created key pair send the X.509 certificate and the CredentialKey certificate chain
 * to the issuing authority, which should evaluate them to decide whether this is a credential that
 * it previously provisioned and still trusts. If so, the issuing authority will generate and return
 * an "issuer certificate", a signed statement of some sort (defined by the standards body that
 * specifies the credential type) that may be used to prove the credential validity to a reader. The
 * issuing authority should also generate and send a new set of static authentication data, some of
 * which may be contained in the signed statement and some of which may be per data-item
 * information.
 * <li>Call {@link #storeAuthenticationData storeAuthenticationData} to store the issuer certificate
 * and static authentication data associated with the specified authentication key name.
 * <li>When calling {@link #getEntries getEntries}, provide one of the authentication key names. The
 * {@link GetEntryResult} that is returned will include the stored issuer certificate and static
 * authentication data, and the authentication code returned by
 * {@link GetEntryResult#getMessageAuthenticationCode} will have been produced using the specified
 * authentication key.
 * </ol>
 */
@SuppressWarnings("unused") // TODO(swillden): Remove this
public class IdentityCredential {

    /**
     * The result of retrieving data entries from a credential.
     */
    public class GetEntryResult {
        private Collection<EntryNamespace> entryNamespaces;
        private byte[] authenticatedData;
        private byte[] messageAuthenticationCode;
        private byte[] issuerCertificate;

        /**
         * @return collection of namespaces containing retrieved entries. May be empty if no data
         *         was retrieved.
         */
        public @NonNull Collection<EntryNamespace> getEntryNamespaces() {
            return entryNamespaces;
        }

        /**
         * Returns a CBOR structure containing the session transcript and the retrieved data, all of
         * which may be cryptographically authenticated to prove to the reader that the data is from
         * a trusted credential.
         *
         * @return CBOR structure containing the session transcript and the retrieved data, all of
         *         which may be cryptographically authenticated to prove to the reader that the data
         *         is from a trusted credential.
         *         <p>
         *         The <a href="https://tools.ietf.org/html/rfc7049">CBOR</a> structure returned is
         *         structured according to the following
         *         <a href="https://tools.ietf.org/html/draft-ietf-cbor-cddl-06">CDDL</a> schema:
         *
         *         <pre>
         *     AuthenticatedData = {
         *         "SessionTranscript" : any,
         *         "Response" : {
         *             DocType =&gt; {
         *                 + Namespace =&gt; DataItems
         *             }
         *         }
         *     }
         *     DocType = tstr
         *     Namespace = tstr
         *     DataItems = {
         *         + Name =&gt; Value
         *     }
         *     Name = tstr
         *     Value = tstr / bstr / int / bool
         *         </pre>
         */
        public @NonNull byte[] getAuthenticatedData() {
            return authenticatedData;
        }

        /**
         * Returns a message authentication code over the data returned by
         * {@link #getAuthenticatedData}, to prove to the reader that the data is from a trusted
         * credential.
         *
         * @return a message authentication code for the data returned by
         *         {@link #getAuthenticatedData}, to prove to the reader that the data is from a
         *         trusted credential. This code is produced by using the key agreement and key
         *         derivation function from the ciphersuite with the authentication private key and
         *         the reader ephemeral public key to compute a shared message authentication code
         *         (MAC) key, then using the MAC function from the ciphersuite to compute a MAC of
         *         the authenticate data.
         *
         *         <p>
         *         A MAC is used rather than a digital signature because it is avoids creating a
         *         non-repudiable signature over data provided by the reader, which can create
         *         privacy risk. The MAC is computed over data provided by the reader but because
         *         the reader can also compute the MAC key, it cannot prove that the MAC was
         *         computed by the prover rather than itself. Because only the prover and reader can
         *         compute the MAC code, the reader can verify the MAC but can't use it to prove
         *         anything to a third party.
         */
        public @Nullable byte[] getMessageAuthenticationCode() {
            return messageAuthenticationCode;
        }

        /**
         * Returns the issuer certificate chain for the data authentication key used, or null if
         * dynamic data authentication was not requested.
         *
         * @return the issuer certificate chain for the data authentication key used, or null if
         *         dynamic data authentication was not requested.
         */
        public @Nullable byte[] getIssuerCertificate() {
            return issuerCertificate;
        }
    }

    /**
     * Returns the certificate chain for the CredentialKey, the public key that is used to
     * authenticate this credential to the issuing authority.
     *
     * @return the certificate chain for the CredentialKey, the public key that is used to
     *         authenticate this credential to the issuing authority.
     */
    public @NonNull X509Certificate[] getCredentialKeyCertificateChain() {
        return null;
    }

    /**
     * Deletes the credential. All methods on this object will throw
     * {@link InvalidCredentialNameException} after this is called, and the credential will no
     * longer be retrievable from the {@link IdentityCredentialStore}.
     */
    public void delete() {

    }

    /**
     * Create an ephemeral key pair to use to establish a secure channel with a reader. Most
     * applications will use only the public key, and only to send it to the reader, allowing the
     * private key to be used internally for {@link #encryptMessageToReader} and
     * {@link #decryptMessageFromReader}. The private key is also provided for applications that
     * wish to use a cipher suite that is not supported by <code>IdentityCredential</code>.
     *
     * @return ephemeral key pair to use to establish a secure channel with a reader.
     */
    public @NonNull KeyPair createEphemeralKeyPair() {
        return null;
    }

    /**
     * Encrypt a message for transmission to the reader.
     *
     * @param messagePlaintext
     *            unencrypted message to encrypt.
     *
     * @return encrypted message.
     */
    public @NonNull byte[] encryptMessageToReader(@NonNull byte[] messagePlaintext) {
        return null;
    }

    /**
     * Decrypt a message from the reader.
     *
     * @param messageCiphertext
     *            encrypted message to decrypt.
     *
     * @return decrypted message.
     */
    public @NonNull byte[] decryptMessageFromReader(@NonNull byte[] messageCiphertext) {
        return null;
    }

    /**
     * Retrieve data entries and associated data from this <code>IdentityCredential</code>.
     *
     * @param entryNamespaces
     *            A Java object containing all of the data elements to be retrieved, organized by
     *            namespace.
     *
     * @param requestMessage
     *            The CBOR-formatted data request message. If non-<code>null</code>,
     *            <code>requestMessage</code> must contain a CBOR structure conforming to the
     *            following CDDL schema:
     *
     *            <pre>
     *     SignedReqest = {
     *         "SessionTranscript" : any,   ; Must contain ephemeral public keys
     *         "Request" : {
     *             ? "DocType" : tstr,
     *             + Namespace =&gt; DataItemNames
     *         }
     *     Namespace = tstr
     *     DataItemNames = [ + tstr ]
     *            </pre>
     *
     *            <p>
     *            This argument may be <code>null</code> if neither reader authentication nor
     *            dynamic data authentication are used.
     *
     * @param readerSignature
     *            An ECDSA signature over the content of <code>requestMessage</code> or
     *            <code>null</code> if reader authentication is not being used.
     *
     * @param readerEphemeralPublicKey
     *            The ephemeral public key provided by the reader to establish a secure session. May
     *            be null if the application is not using the secure session functionality
     *            ({@link #encryptMessageToReader} and {@link #decryptMessageFromReader}) provided
     *            by <code>IdentityCredential</code>.
     *
     * @param authenticationKeyName
     *            The name of the dynamic authentication key and associated static data
     *            authentication set to use for data authentication. May be <code>null</code> if
     *            dynamic authentication is not being used.
     *
     * @return result object containing entry data organized by namespace and a crytographically
     *         authenticated representation of the same data.
     */
    public GetEntryResult getEntries(@NonNull Collection<EntryNamespace> entryNamespaces,
            @Nullable byte[] requestMessage, @Nullable byte[] readerSignature,
            PublicKey readerEphemeralPublicKey, @Nullable String authenticationKeyName) {
        return null;
    }

    /**
     * Generate a key pair to use for dynamic authentication / anti-cloning protection.
     *
     * Note that creating a key pair is not enough. Readers must have some reason to trust the key,
     * which means an issuing authority-provided
     *
     * @param authenticationKeyName
     *
     * @return an X.509 certificate containing a keystore attestation extension for the generated
     *         public key.
     */
    public X509Certificate generateAuthenticationKeyPair(String authenticationKeyName) {
        return null;
    }

    /**
     * Store authentication data associated with a dynamic authentication key.
     *
     * @param authenticationKeyName
     *            The name of the dynamic authentication key for which certification and associated
     *            static authentication data is being provided.
     *
     * @param issuerCert
     *            The issuing authority-provided data that certifies the authentication key pair for
     *            use. May be an X.509 certificate or some other format, as specified by the
     *            relevant identity credential standard.
     *
     * @param staticAuthData
     *            Static authentication data provided by the issuer that validates the authenticity
     *            and integrity of the credential data fields. The data is structured as a map from
     *            namespace/name pairs to byte arrays.
     *
     * @param storeForDirectAccess
     *            If direct access is supported for this credential, also store this authentication
     *            key and static authentication data in the security hardware for use in direct
     *            access.
     */
    public void storeAuthenticationData(String authenticationKeyName, byte[] issuerCert,
            Map<Pair<String, String>, byte[]> staticAuthData, boolean storeForDirectAccess) {}

    /**
     *
     * @param authenticationKeyName
     * @return
     */
    public int getAuthenticationDataUsageCount(String authenticationKeyName) {
        return 0;
    }


    /**
     *
     * @param authenticationKeyName
     */
    public void deleteAuthenticationData(String authenticationKeyName) {}

}
