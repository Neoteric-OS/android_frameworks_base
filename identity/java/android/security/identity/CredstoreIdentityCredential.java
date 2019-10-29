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
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.biometrics.BiometricPrompt;
import android.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class CredstoreIdentityCredential extends IdentityCredential {

    private static final String TAG = "CredstoreIdentityCredential";
    private String mCredentialName;
    private @IdentityCredentialStore.Ciphersuite int mCipherSuite;
    private Context mContext;
    private ICredential mBinder;

    CredstoreIdentityCredential(Context context, String credentialName,
            @IdentityCredentialStore.Ciphersuite int cipherSuite,
            ICredential binder) {
        mContext = context;
        mCredentialName = credentialName;
        mCipherSuite = cipherSuite;
        mBinder = binder;
    }

    private KeyPair mEphemeralKeyPair = null;
    private SecretKey mSecretKey = null;
    private SecureRandom mSecureRandom = null;
    private int mEphemeralCounter;

    @Override
    public @NonNull KeyPair createEphemeralKeyPair() throws IdentityCredentialException {
        try {
            // This PKCS#12 blob is generated in credstore, using BoringSSL.
            //
            // The main reason for this convoluted approach and not just sending the decomposed
            // key-pair is that this would require directly using (device-side) BouncyCastle which
            // is tricky due to various API hiding efforts. So instead we have credstore generate
            // this PKCS#12 blob. The blob is encrypted with no password (sadly, also, BoringSSL
            // doesn't support not using encryption when building a PKCS#12 blob).
            //
            byte[] pkcs12 = mBinder.createEphemeralKeyPair();
            String alias = "ephemeralKey";
            char[] password = {};

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ByteArrayInputStream bais = new ByteArrayInputStream(pkcs12);
            ks.load(bais, password);
            PrivateKey privKey = (PrivateKey) ks.getKey(alias, password);

            Certificate cert = ks.getCertificate(alias);
            PublicKey pubKey = cert.getPublicKey();

            mEphemeralKeyPair = new KeyPair(pubKey, privKey);
            return mEphemeralKeyPair;
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        } catch (KeyStoreException
                | CertificateException
                | UnrecoverableKeyException
                | NoSuchAlgorithmException
                | IOException e) {
            throw new RuntimeException("Unexpected exception ", e);
        }
    }

    @Override
    public void setReaderEphemeralPublicKey(@NonNull PublicKey readerEphemeralPublicKey)
            throws IdentityCredentialException {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(mEphemeralKeyPair.getPrivate());
            ka.doPhase(readerEphemeralPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] salt = new byte[0];
            byte[] info = new byte[0];
            byte[] derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);

            mSecretKey = new SecretKeySpec(derivedKey, "AES");

            mSecureRandom = new SecureRandom();

            mEphemeralCounter = 2;

        } catch (InvalidKeyException
                | NoSuchAlgorithmException e) {
            throw new IdentityCredentialException("Error performing key agreement", e);
        }
    }

    @Override
    public @NonNull byte[] encryptMessageToReader(@NonNull byte[] messagePlaintext)
            throws IdentityCredentialException {
        byte[] messageCiphertextAndAuthTag = null;
        try {
            ByteBuffer iv = ByteBuffer.allocate(12);
            iv.putInt(8, mEphemeralCounter);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec encryptionParameterSpec = new GCMParameterSpec(128, iv.array());
            cipher.init(Cipher.ENCRYPT_MODE, mSecretKey, encryptionParameterSpec);
            messageCiphertextAndAuthTag = cipher.doFinal(messagePlaintext);
        } catch (BadPaddingException
                | IllegalBlockSizeException
                | NoSuchPaddingException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            throw new IdentityCredentialException("Error encrypting message", e);
        }
        mEphemeralCounter += 2;
        return messageCiphertextAndAuthTag;
    }

    @Override
    public @NonNull byte[] decryptMessageFromReader(@NonNull byte[] messageCiphertext)
            throws IdentityCredentialException {
        int expectedCounter = mEphemeralCounter - 1;
        ByteBuffer iv = ByteBuffer.allocate(12);
        iv.putInt(8, expectedCounter);
        byte[] plainText = null;
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, mSecretKey, new GCMParameterSpec(128, iv.array()));
            plainText = cipher.doFinal(messageCiphertext);
        } catch (BadPaddingException
                | IllegalBlockSizeException
                | InvalidAlgorithmParameterException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchPaddingException e) {
            throw new IdentityCredentialException("Error decrypting message", e);
        }
        return plainText;
    }

    @Override
    public @NonNull Collection<X509Certificate> getCredentialKeyCertificateChain()
            throws IdentityCredentialException {
        try {
            byte[] certsBlob = mBinder.getCredentialKeyCertificateChain();
            ByteArrayInputStream bais = new ByteArrayInputStream(certsBlob);

            Collection<? extends Certificate> certs = null;
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                certs = factory.generateCertificates(bais);
            } catch (CertificateException e) {
                throw new IdentityCredentialException("Error decoding certificates", e);
            }

            LinkedList<X509Certificate> x509Certs = new LinkedList<>();
            for (Certificate cert : certs) {
                x509Certs.add((X509Certificate) cert);
            }
            return x509Certs;
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        }
    }

    private boolean mAllowUsingExhaustedKeys = true;

    @Override
    public void setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys) {
        mAllowUsingExhaustedKeys = allowUsingExhaustedKeys;
    }

    @NonNull
    @Override
    public BiometricPrompt.CryptoObject getCryptoObject() throws IdentityCredentialException {
        // TODO
        return null;
    }

    @NonNull
    @Override
    public GetEntryResult getEntries(
            @Nullable byte[] requestMessage,
            @NonNull Collection<RequestNamespace> entriesToRequest,
            @Nullable byte[] sessionTranscript,
            @Nullable byte[] readerSignature) throws IdentityCredentialException {

        RequestNamespaceParcel[] rnsParcels = new RequestNamespaceParcel[entriesToRequest.size()];
        int n = 0;
        for (RequestNamespace rns : entriesToRequest) {
            rnsParcels[n] = new RequestNamespaceParcel();
            rnsParcels[n].namespaceName = rns.getNamespaceName();
            Collection<Pair<String, Boolean>> entries = rns.getEntries();
            rnsParcels[n].entries = new RequestEntryParcel[entries.size()];
            int m = 0;
            for (Pair<String, Boolean> requestedEntry : entries) {
                rnsParcels[n].entries[m] = new RequestEntryParcel();
                rnsParcels[n].entries[m].name = requestedEntry.first;
                rnsParcels[n].entries[m].authenticate = requestedEntry.second;
                m++;
            }
            n++;
        }

        GetEntryResultParcel resultParcel = null;
        try {
            resultParcel = mBinder.getEntries(
                requestMessage != null ? requestMessage : new byte[0],
                rnsParcels,
                sessionTranscript != null ? sessionTranscript : new byte[0],
                readerSignature != null ? readerSignature : new byte[0],
                mAllowUsingExhaustedKeys);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            if (e.errorCode == ICredentialStore.ERROR_EPHEMERAL_PUBLIC_KEY_NOT_FOUND) {
                throw new EphemeralPublicKeyNotFoundException(e.getMessage(), e);
            } else if (e.errorCode == ICredentialStore.ERROR_INVALID_READER_CERTIFICATE_CHAIN) {
                throw new InvalidReaderCertificateChainException(e.getMessage(), e);
            } else if (e.errorCode == ICredentialStore.ERROR_NO_AUTHENTICATION_KEY_AVAILABLE) {
                throw new NoAuthenticationKeyAvailableException(e.getMessage(), e);
            } else {
                throw new RuntimeException("Unexpected ServiceSpecificException with code "
                        + e.errorCode, e);
            }
        }

        GetEntryResult result = new GetEntryResult();
        for (ResultNamespaceParcel resultNamespaceParcel : resultParcel.resultNamespaces) {
            ResultNamespace.Builder builder =
                    new ResultNamespace.Builder(resultNamespaceParcel.namespaceName);
            for (ResultEntryParcel resultEntryParcel : resultNamespaceParcel.entries) {
                if (resultEntryParcel.status == ICredential.STATUS_OK) {
                    builder.addEntry(resultEntryParcel.name, resultEntryParcel.value);
                } else {
                    builder.addErrorStatus(resultEntryParcel.name, resultEntryParcel.status);
                }
            }
            result.mEntryNamespaces.add(builder.build());
        }
        result.mStaticAuthenticationData = resultParcel.staticAuthenticationData;
        result.mAuthenticatedData = resultParcel.deviceNameSpaces;
        result.mEcdsaSignature = resultParcel.signature;
        if (result.mEcdsaSignature != null && result.mEcdsaSignature.length == 0) {
            result.mEcdsaSignature = null;
        }
        result.mMessageAuthenticationCode = resultParcel.mac;
        if (result.mMessageAuthenticationCode != null
                && result.mMessageAuthenticationCode.length == 0) {
            result.mMessageAuthenticationCode = null;
        }
        return result;
    }

    @Override
    public void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey)
            throws IdentityCredentialException {
        try {
            mBinder.setAvailableAuthenticationKeys(keyCount, maxUsesPerKey);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        }
    }

    @Override
    public @NonNull Collection<X509Certificate> getAuthKeysNeedingCertification()
            throws IdentityCredentialException {
        try {
            AuthKeyParcel[] authKeyParcels = mBinder.getAuthKeysNeedingCertification();
            LinkedList<X509Certificate> x509Certs = new LinkedList<>();
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (AuthKeyParcel authKeyParcel : authKeyParcels) {
                Collection<? extends Certificate> certs = null;
                ByteArrayInputStream bais = new ByteArrayInputStream(authKeyParcel.x509cert);
                certs = factory.generateCertificates(bais);
                if (certs.size() != 1) {
                    throw new IdentityCredentialException("Returned blob yields more "
                            + "than one X509 cert");
                }
                X509Certificate authKeyCert = (X509Certificate) certs.iterator().next();
                x509Certs.add(authKeyCert);
            }
            return x509Certs;
        } catch (CertificateException e) {
            throw new IdentityCredentialException("Error decoding authenticationKey", e);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        }
    }

    @Override
    public void storeStaticAuthenticationData(X509Certificate authenticationKey,
            byte[] staticAuthData)
            throws IdentityCredentialException {
        try {
            AuthKeyParcel authKeyParcel = new AuthKeyParcel();
            authKeyParcel.x509cert = authenticationKey.getEncoded();
            mBinder.storeStaticAuthenticationData(authKeyParcel, staticAuthData);
        } catch (CertificateEncodingException e) {
            throw new IdentityCredentialException("Error encoding authenticationKey", e);
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        }
    }

    @Override
    public @NonNull int[] getAuthenticationDataUsageCount() throws IdentityCredentialException {
        try {
            int[] usageCount = mBinder.getAuthenticationDataUsageCount();
            return usageCount;
        } catch (android.os.RemoteException e) {
            throw new RuntimeException("Unexpected RemoteException ", e);
        } catch (android.os.ServiceSpecificException e) {
            throw new RuntimeException("Unexpected ServiceSpecificException with code "
                    + e.errorCode, e);
        }
    }
}
