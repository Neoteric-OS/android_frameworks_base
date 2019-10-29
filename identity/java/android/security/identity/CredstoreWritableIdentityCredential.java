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
import android.content.Context;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;

class CredstoreWritableIdentityCredential extends WritableIdentityCredential {

    private static final String TAG = "CredstoreWritableIdentityCredential";

    private String mDocType;
    private String mCredentialName;
    private Context mContext;
    private IWritableCredential mBinder;

    CredstoreWritableIdentityCredential(Context context,
            @NonNull String credentialName,
            @NonNull String docType,
            IWritableCredential binder) {
        mContext = context;
        mDocType = docType;
        mCredentialName = credentialName;
        mBinder = binder;
    }

    @NonNull @Override
    public Collection<X509Certificate> getCredentialKeyCertificateChain(@NonNull byte[] challenge)
            throws IdentityCredentialException {
        try {
            byte[] certsBlob = mBinder.getCredentialKeyCertificateChain(challenge);
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
        } catch (Exception e) {
            e.printStackTrace();
            throw new IdentityCredentialException("Error", e); // TODO
        }
    }

    @NonNull @Override
    public byte[] personalize(@NonNull Collection<AccessControlProfile> accessControlProfiles,
            @NonNull Collection<EntryNamespace> entryNamespaces)
            throws IdentityCredentialException {
        int n;

        AccessControlProfileParcel[] acpParcels =
                new AccessControlProfileParcel[accessControlProfiles.size()];
        n = 0;
        for (AccessControlProfile profile : accessControlProfiles) {
            acpParcels[n] = new AccessControlProfileParcel();
            acpParcels[n].id = profile.getAccessControlProfileId();
            X509Certificate cert = profile.getReaderCertificate();
            if (cert != null) {
                try {
                    acpParcels[n].readerCertificate = cert.getEncoded();
                } catch (CertificateException e) {
                    throw new IdentityCredentialException("Error encoding reader certificate", e);
                }
            } else {
                acpParcels[n].readerCertificate = new byte[0];
            }
            acpParcels[n].userAuthenticationRequired = profile.isUserAuthenticationRequired();
            acpParcels[n].userAuthenticationTimeout = profile.getUserAuthenticationTimeout();
            n++;
        }

        EntryNamespaceParcel[] ensParcels  = new EntryNamespaceParcel[entryNamespaces.size()];
        n = 0;
        for (EntryNamespace ens : entryNamespaces) {
            ensParcels[n] = new EntryNamespaceParcel();
            ensParcels[n].namespaceName = ens.getNamespaceName();

            Collection<String> entryNames = ens.getEntryNames();
            EntryParcel[] eParcels = new EntryParcel[entryNames.size()];
            int m = 0;
            for (String entryName : entryNames) {
                eParcels[m] = new EntryParcel();
                eParcels[m].name = entryName;
                eParcels[m].value = ens.getEntryValue(entryName);
                Collection<Integer> acpIds = ens.getAccessControlProfileIds(entryName);
                eParcels[m].accessControlProfileIds = new int[acpIds.size()];
                int o = 0;
                for (Integer acpId : acpIds) {
                    eParcels[m].accessControlProfileIds[o++] = acpId;
                }
                m++;
            }
            ensParcels[n].entries = eParcels;
            n++;
        }


        try {
            byte[] personalizationReceipt = mBinder.personalize(acpParcels, ensParcels);
            return personalizationReceipt;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IdentityCredentialException("Error", e); // TODO
        }
    }

}
