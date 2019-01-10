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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.security.identity.credstore.IIdentityCredentialStore;
import android.security.identity.credstore.ResultCode;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * <p>
 * IdentityCredentialStore stores credentials that identify a person.
 * </p>
 *
 * <p>
 * This credential store makes use of security hardware, where available, to secure the identity
 * data. It enables the issuing authority to specify access control conditions which are enforced by
 * the security hardware, including reader authentication and user authentication. It enables
 * credential validity to be cryptographically verified in two ways, with static digital signatures
 * provided by the issuing authority, to prove authenticity and integrity, and with
 * dynamically-generated digital signatures provided by the security hardware, to prove that the
 * credential hasn't been cloned.
 * </p>
 *
 * <p>
 * An IdentityCredential is usually presented to readers via mechanisms implemented by the Android
 * application that owns it, for example, through NFC tap via Host Card Emulation. The Android
 * application and device running are collectively known as the <em>prover</em> in this
 * documentation. In addition to the access controls provided by the security hardware, it is
 * recommended that apps implement their own access control, perhaps requesting user permission to
 * share sensitive data elements.
 * </p>
 *
 * <p>
 * For secure hardware that supports it, {@code IdentityCredential}s can also be presented to
 * readers via "direct access" to allow use of IdentityCredentials when the mobile device battery's
 * state of charge is too low for normal operation, that is when Android cannot be booted but there
 * is enough power available to run the secure hardware and a low-power communication channel, such
 * as NFC. For direct access mode to function, the secure hardware must have support for the
 * protocols used for the specific type of credential, and the app must have requested direct access
 * support for each on data element that should be made directly available. See
 * {@link WritableIdentityCredential#personalize} and {@link EntryNamespace}.
 * </p>
 *
 * <p>
 * Creation and provisioning of an {@code IdentityCredential} is a multi-step process. The
 * complexity is necessary to ensure that the issuing authority's server can:
 *
 * <ul>
 * <li>Ensure that it trusts the secure hardware before provisioning;</li>
 * <li>Validate that correct data is provisioned; and</li>
 * <li>Prepare necessary keys and certificates to prove the validity of the data to readers.</li>
 * </ul>
 *
 * <p>
 * To fully provision an {@code IdentityCredential}:
 *
 * <ol>
 * <li>(Optionally) Use {@link #getSecurityHardwareType} and
 * {@link #getSecurityHardwareCertification} to verify that the available secure hardware appears to
 * satisfy the issuing authority requirements.</li>
 * <li>Call {@link #createCredential} to create a {@link WritableIdentityCredential}.</li>
 * <li>(Optionally) Call {@link WritableIdentityCredential#getCredentialKeyCertificateChain} to get
 * a certificate for a newly-created public key, called the CredentialKey, that identifies this
 * credential to the issuing authority. The certificate contains an
 * <a href="https://developer.android.com/training/articles/security-key-attestation">Android
 * Keystore attestation extension</a> that describes the secure hardware for the issuing authority
 * to validate. The issuing authority server uses the certificate to determine if the secure
 * hardware meets requirements.</li>
 * <li>Call {@link WritableIdentityCredential#personalize} to to specify the set of named access
 * control profiles that the issuing authority wishes to use to control access to data elements to
 * be provisioned and to store data elements. The return value is a CBOR data structure containing
 * all of the provisioned access control profiles and data elements, signed by the CredentialKey.
 * This signed data structure may be returned to the issuing authority server, so it can verify that
 * the data was provisioned correctly.</li>
 * <li>Call {@link IdentityCredential#setAvailableAuthenticationKeys} to specify the number of
 * dynamic authentication keys the {@code IdentityCredential>} should maintain, and the number of
 * times each should be used.
 * <li>Call {@link IdentityCredential#getAuthKeysNeedingCertification} to get the set of dynamic
 * authentication keys that require issuer certification. Send them and the CredentialKey
 * certificate chain (see {@link IdentityCredential#getCredentialKeyCertificateChain}) to the issuer
 * for certification.
 * <li>For each dynamic authentication key certified by the issuer, call
 * {@link IdentityCredential#storeAuthenticationData IdentityCredential.storeAuthenticationData}, to
 * store the certificate and associated static authentication data provided by the issuing
 * authority. This "certificate" may be an X.509 certificate or whatever other structure the issuing
 * authority wishes to use to prove signing key authorization.
 * </ol>
 */
@SuppressWarnings("unused")  // TODO(swillden): Remove this.
public class IdentityCredentialStore {
    private static final String TAG = "IdentityCredentialStore";
    private static IdentityCredentialStore sInstance = new IdentityCredentialStore();
    private IIdentityCredentialStore mBinder;

    /** @hide */
    @IntDef(prefix = {"SECURITY_HARDWARE"},
            value = {SECURITY_HARDWARE_SOFTWARE_ONLY,
                     SECURITY_HARDWARE_TRUSTED_EXECUTION_ENVIRONMENT,
                     SECURITY_HARDWARE_EMBEDDED_SECURE_CPU, SECURITY_HARDWARE_DISCRETE_SECURE_CPU})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SecurityHardwareType {}

    /**
     * Indicates that the Identity Credential Store is implemented in Android. The implementation
     * runs in a separate process that has restricted access and a narrow attack surface, but an
     * attacker who compromises the Android kernel can obtain the private CredentialKey and bypass
     * the access control requirements specified during provisioning.
     */
    public static final int SECURITY_HARDWARE_SOFTWARE_ONLY = 0;

    /**
     * Indicates that the Identity Credential store is implemented in a Trusted Execution
     * Environment. This means it executes on the same CPU or set of CPUs as Android kernel and user
     * code, but in an isolated environment. To obtain the private CredentialKey or bypass the
     * access control requirements specified during provisioning, an attacker would need to
     * compromise the Trusted Execution Environment; compromising Android, even the kernel, would
     * not be sufficient. However, because Trusted Execution Environments share resources with the
     * CPU running Android, it is possible that side channel attacks could extract data or secrets.
     * In addition, Trusted Execution Environments are not designed to resist attacks on the
     * physical hardware.
     */
    public static final int SECURITY_HARDWARE_TRUSTED_EXECUTION_ENVIRONMENT = 1;

    /**
     * Indicates that the Identity Credential store is implemented in a separate CPU from the
     * processor(s) that run Android kernel and user code, but that the security CPU is embedded in
     * the same physical package as the Android processor(s). Embedded secure CPUs are designed to
     * resist physical hardware attacks. Barring some egregious implementation error, this sort of
     * implementation is strictly more secure than one that runs in a
     * {@link #SECURITY_HARDWARE_TRUSTED_EXECUTION_ENVIRONMENT}, and has some security advantages
     * and some security disadvantages as compared to a
     * {@link #SECURITY_HARDWARE_DISCRETE_SECURE_CPU}. This sort of implementation is almost
     * certainly not capable of supporting direct access, though, so credentials will only be usable
     * when the Android device is fully-powered.
     */
    public static final int SECURITY_HARDWARE_EMBEDDED_SECURE_CPU = 2;

    /**
     * Indicates that the Identity Credential store is implemented in a separate CPU from the
     * processor(s) that run Android and user code, and the CPU is in a discrete, purpose-built
     * package and includes its own internal storage. Discrete secure CPUs are designed to resist
     * physical hardware attacks. Barring some egregious implementation error, this sort of
     * implementation is strictly more secure than one that runs in a
     * {@link #SECURITY_HARDWARE_TRUSTED_EXECUTION_ENVIRONMENT}, and has some security advantages
     * and some security disadvantages as compared to a
     * {@link #SECURITY_HARDWARE_EMBEDDED_SECURE_CPU}. This sort of implementation may be capable of
     * supporting direct access.
     */
    public static final int SECURITY_HARDWARE_DISCRETE_SECURE_CPU = 3;

    /** @hide */
    @IntDef(prefix = {"SECURITY_CERTIFICATION"},
            value = {SECURITY_CERTIFICATION_UNCERTIFIED, SECURITY_CERTIFICATION_CC_EAL_4_PLUS,
                     SECURITY_CERTIFICATION_CC_EAL_5_PLUS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SecurityCertification {}

    /**
     * Indicates that the Identity Credential store implementation is not certified. It has not been
     * validated by an accredited evaluator and its security is unknown. It may be secure or it may
     * not. Application developers and Identity Credential issuing authorities should carefully
     * evaluate the privacy and security risks. For many use cases, uncertified devices are fine.
     * Static digital signatures will still provide strong proof of authenticity and integrity even
     * if the device is compromised. Cloning protection may or may not be strong, though, and it may
     * be that access control can be bypassed.
     */
    public static final int SECURITY_CERTIFICATION_UNCERTIFIED = 0;

    /**
     * Indicates that the Identity Credential store implementation has been evaluated for security
     * and certified. The security hardware was evaluated against the Secure IC Protection Profile,
     * <a href=
     * "https://www.commoncriteriaportal.org/files/ppfiles/pp0084b_pdf.pdf">BSI-CC-PP-0084-2014</a>.
     * In addition, the firmware was evaluated by a nationally accredited testing laboratory against
     * the "High" attack potential standard defined in
     * <a href="https://www.commoncriteriaportal.org/files/supdocs/CCDB-2013-05-002.pdf">Common
     * Criteria Application of Attack Potential to Smartcards</a>.
     */
    public static final int SECURITY_CERTIFICATION_CC_EAL_4_PLUS = 1;

    /**
     * Indicates that the Identity Credential store implementation has been evaluated for security
     * and certified. The security hardware was evaluated against the Secure IC Protection Profile,
     * <a href=
     * "https://www.commoncriteriaportal.org/files/ppfiles/pp0084b_pdf.pdf">BSI-CC-PP-0084-2014</a>,
     * with the protection profile's requirements increased to require Common Criteria Evaluation
     * Assurance Level 5. In addition, the firmware was evaluated by a nationally accredited testing
     * laboratory against the "High" attack potential standard defined in
     * <a href="https://www.commoncriteriaportal.org/files/supdocs/CCDB-2013-05-002.pdf">Common
     * Criteria Application of Attack Potential to Smartcards</a>.
     */
    public static final int SECURITY_CERTIFICATION_CC_EAL_5_PLUS = 2;

    /**
     * Indicates that the Identity Credential store implementation has been evaluated for security
     * and certified. The security hardware was evaluated against the Secure IC Protection Profile,
     * <a href=
     * "https://www.commoncriteriaportal.org/files/ppfiles/pp0084b_pdf.pdf">BSI-CC-PP-0084-2014</a>,
     * with the protection profile's requirements increased to require Common Criteria Evaluation
     * Assurance Level 6. In addition, the firmware was evaluated by a nationally accredited testing
     * laboratory against the "High" attack potential standard defined in
     * <a href="https://www.commoncriteriaportal.org/files/supdocs/CCDB-2013-05-002.pdf">Common
     * Criteria Application of Attack Potential to Smartcards</a>.
     */
    public static final int SECURITY_CERTIFICATION_CC_EAL_6_PLUS = 2;

    /** @hide */
    @IntDef(prefix = {"CipherSuite"},
            value = {CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_128_GCM_SHA256})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Ciphersuite {}

    /**
     * Specifies that the ciphersuite that will be used to secure communications between the reader
     * is:
     *
     * <ul>
     * <li>ECDHE with HKDF-SHA-256 for key agreement;
     * <li>AES-128 with GCM block mode for authenticated encryption (nonces are incremented by one
     * for every message); and
     * <li>ECDSA with SHA-256 for signing (used for signing session transcripts to defeat
     * man-in-the-middle attacks), signing keys are not ephemeral. See {@link IdentityCredential}
     * for details on reader and prover signing keys.
     * </ul>
     *
     * <p>
     * At present this is the only ciphersuite offered by {@code IdentityCredential}s.
     */
    public static final int CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_128_GCM_SHA256 = 1;

    /** @hide */
    @IntDef(prefix = {"ReturnCode"},
            value = {RETURN_CODE_OK, RETURN_CODE_UNIMPLEMENTED, RETURN_CODE_DATABASE_ERROR,
                     RETURN_CODE_INVARIANT_ERROR, RETURN_CODE_CHUNKING_ERROR,
                     RETURN_CODE_INVALID_ARGUMENT_ERROR, RETURN_CODE_CBOR_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReturnCode {}

    /**
     * Fill in
     */
    public static final int RETURN_CODE_OK = 0;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_UNIMPLEMENTED = 1;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_DATABASE_ERROR = 2;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_INVARIANT_ERROR = 3;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_CHUNKING_ERROR = 4;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_INVALID_ARGUMENT_ERROR = 5;

    /**
     * Fill in
     */
    public static final int RETURN_CODE_CBOR_ERROR = 6;

    /**
     * Get the {@code IdentityCredentialStore}.
     *
     * @return the {@code IdentityCredentialStore}.
     */
    public static IdentityCredentialStore getInstance() {
        return sInstance;
    }

    private IdentityCredentialStore() {
        mBinder = IIdentityCredentialStore.Stub.asInterface(
            ServiceManager.getService("android.security.identity"));
        if (mBinder == null) {
            // Throw an appropriate exception
        }
    }

    /**
     * Returns the type of secure hardware. Note that {@link #SECURITY_HARDWARE_SOFTWARE_ONLY} is
     * acceptable for many types of identity credentials. Unless you have specific reasons to need
     * something better, you probably don't.
     *
     * @return the type of secure hardware, one of {@link #SECURITY_HARDWARE_SOFTWARE_ONLY},
     *         {@link #SECURITY_HARDWARE_EMBEDDED_SECURE_CPU} or
     *         {@link #SECURITY_HARDWARE_DISCRETE_SECURE_CPU}
     */
    public @SecurityHardwareType int getSecurityHardwareType() {
        try {
            ResultCode result = new ResultCode();
            int securityHardwareType = mBinder.getSecurityHardwareType(result);
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw the proper exception
            }
            return securityHardwareType;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the certification level of the security hardware.
     *
     * Note that certified security hardware is not required for all identity credentials. Unless
     * you have specific reasons to need certified hardware, you probably don't.
     *
     * @return the certification level of the security hardware, one of one of
     *         {@link #SECURITY_CERTIFICATION_UNCERTIFIED},
     *         {@link #SECURITY_CERTIFICATION_CC_EAL_4_PLUS} or
     *         {@link #SECURITY_CERTIFICATION_CC_EAL_5_PLUS}
     */
    public @SecurityCertification int[] getSecurityHardwareCertifications() {
        try {
            ResultCode result = new ResultCode();
            int[] securityHardwareCerts = mBinder.getSecurityHardwareCertifications(result);
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw proper exception
            }
            return securityHardwareCerts;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a collection of document types for which the underlying secure hardware can provide
     * direct access. Document types are strings which are standardized by the relevant standards
     * body. For example ISO 18013 mobile driving licenses have the type "org.iso.18013".
     *
     * Note that it's unlikely that any type of security hardware other than
     * {@link #SECURITY_HARDWARE_DISCRETE_SECURE_CPU} can support direct access.
     *
     * @return directly-accessible document types.
     */
    public Iterable<String> getDirectAccessDocTypes() {
        try {
            ArrayList<String> docTypes = new ArrayList<String>();
            ResultCode result = mBinder.getDirectAccessDocTypes(docTypes);
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw proper exception
            }
            return docTypes;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a new Identity Credential.
     *
     * Identity Credentials are visible only to the application that creates them.
     *
     * @param credentialName
     *            The name of this credential. This name is used by the app to identify credentials
     *            it creates.
     *
     * @param docType
     *            The type of credential. Document types are strings which are standardized by the
     *            relevant standards body. For example, ISO 18013 mobile driving licenses have the
     *            type "org.iso.18013". A document may have multiple types where appropriate. For
     *            example, if the state of California issues a driving license that conforms to the
     *            ISO standard it will use the ISO doctype, but because the ISO standard permits
     *            issuing agencies to extend the driving license, it may also be useful to specify
     *            something like "gov.ca.dmv.dl" (note that that string is hypothetical; it would be
     *            up to the state of California to define the actual doctype). The document type
     *            serves two purposes, currently:
     *
     *            <ol>
     *            <li>To communicate to underlying security hardware the type of the credential, for
     *            use in direct access mode if the security hardware supports direct access for this
     *            document type.
     *            <li>To enable an app to respond to reader requests for a specific document type.
     *            </ol>
     *
     * @param cipherSuite
     *            The cipher suite that will be used to secure communications with readers.
     *
     * @return A {@link WritableIdentityCredential} object, which is used to provision data into the
     *         newly-created credential.
     */
    public WritableIdentityCredential createCredential(@NonNull String credentialName,
                                                       @NonNull String docType,
                                                       @Ciphersuite int cipherSuite)
                throws InvalidCredentialNameException, UnsupportedCipherSuiteException {
        try {
            ResultCode result = new ResultCode();
            WritableIdentityCredential credential = new WritableIdentityCredential(
                                                        mBinder.createCredential(credentialName,
                                                                                 docType,
                                                                                 cipherSuite,
                                                                                 result));
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw proper exception
            }
            return credential;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }

    /**
     * Retrieve a named credential.
     *
     * @param credentialName
     *            the name of the credential to retrieve.
     *
     * @return The named credential, or null if not found.
     */
    public @Nullable IdentityCredential getCredentialByName(@NonNull String credentialName) {
        try {
            ResultCode result = new ResultCode();
            IdentityCredential credential =
                    new IdentityCredential(mBinder.getCredentialByName(credentialName, result));
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw proper exception
            }
            return credential;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }

    /**
     * Retrieve all credentials of a given type.
     *
     * @param docType
     *            The desired document type.  Use {@code null} to retrieve all credentials.
     *
     * @return Documents of the specified type. Will be empty if no matching credentials are found.
     */
    public @NonNull Iterable<String> getCredentialNamesByDocType(@Nullable String docType) {
        try {
            ArrayList<String> credentials = new ArrayList<String>();
            ResultCode result = mBinder.getCredentialNamesByDocType(docType, credentials);
            if (result.errorCode != RETURN_CODE_OK) {
                //TODO(jbires): Throw proper exception
            }
            return credentials;
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot connect to credstore", e);
            throw new AssertionError(e);
        }
    }
}
