/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.nfc;

import com.android.ca.trustpoint.m2m.EntityName;
import com.android.ca.trustpoint.m2m.EntityNameAttribute;
import com.android.ca.trustpoint.m2m.EntityNameAttributeId;
import com.android.ca.trustpoint.m2m.KeyAlgorithmDefinition;
import com.android.ca.trustpoint.m2m.M2mCertificate;
import com.android.ca.trustpoint.m2m.M2mProvider;
import com.android.ca.trustpoint.m2m.SuperiorCertData;

import com.android.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.org.bouncycastle.asn1.DERInteger;
import com.android.org.bouncycastle.asn1.DERSequence;
import com.android.org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import com.android.org.bouncycastle.asn1.x500.RDN;
import com.android.org.bouncycastle.asn1.x500.X500Name;
import com.android.org.bouncycastle.asn1.x500.style.BCStyle;
import com.android.org.bouncycastle.asn1.x500.style.IETFUtils;
import com.android.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.android.org.conscrypt.TrustedCertificateStore;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * An util class to verify NDEF (NFC Data Exchange Format) Signature Records as defined in the NFC
 * Forum Signature Record Type Definition Technical Specification Version 2.0.
 */
public class NdefMessageSignatureVerifier {
    /**
     * Verify signature.
     *
     * @param message data to be verified
     * @param sigTypeAlg signature algorithm
     * @param hashType digest type
     * @param signature data signature
     * @param issuerPubKey issuer public key
     *
     * @return true if passed.
     *         false if failed.
     *
     * @throws InvalidKeyException if issuerPubKey is invalid
     * @throws IOException on retrieving signature or certificate errors from URI
     * @throws NoSuchAlgorithmException if signature algorithm is unknown
     * @throws NoSuchProviderException if signature algorithm provider is unknown
     * @throws SignatureException if signature is wrong
     */
    private static boolean verifySignature(byte[] message, String sigTypeAlg, String hashType,
                                           byte[] signature, PublicKey issuerPubKey)
            throws InvalidKeyException, IOException, NoSuchAlgorithmException,
                   NoSuchProviderException, SignatureException {
        // Build the Bouncy Castle Signature Algorithm which is a
        // combination of HASH + 'with' + ALG
        Signature sig = Signature.getInstance(hashType + "with" + sigTypeAlg,
                                              BouncyCastleProvider.PROVIDER_NAME);
        sig.initVerify(issuerPubKey);
        sig.update(message);

        if (sigTypeAlg.equals("ECDSA")) {
            // Convert the signature from two concatenated octet strings
            // (r,s) to an ASN1Sequence, prepending a 0 byte to each of r
            // and s if their MSByte has a MSBit of 1
            int rslength = signature.length / 2;
            int rstart = 0;
            int sstart = 0;
            if ((signature[0] & 0x80) == 0x80) {
                rstart = 1;
            }
            if ((signature[rslength] & 0x80) == 0x80) {
                sstart = 1;
            }
            byte[] temp1 = new byte[rstart + rslength];
            temp1[0] = 0;
            byte[] temp2 = new byte[sstart + rslength];
            temp2[0] = 0;

            System.arraycopy(signature,        0, temp1, rstart, rslength);
            DERInteger r = new DERInteger(temp1);
            System.arraycopy(signature, rslength, temp2, sstart, rslength);
            DERInteger s = new DERInteger(temp2);

            ASN1EncodableVector v = new ASN1EncodableVector();

            v.add(r);
            v.add(s);

            byte[] sigBytes = new DERSequence(v).getEncoded();

            return sig.verify(sigBytes);
        } else {
            return sig.verify(signature);
        }
    }

    /**
     * Read a byte from buffer.
     *
     * @param buffer a byte arrary input stream
     *
     * @return an integer value of the byte read.
     *
     * @throws FormatException on out of data
     */
    private static int readByte(ByteArrayInputStream buffer)
        throws FormatException {
        int data = buffer.read();
        if (data == -1) {
            throw new FormatException("Not enough data in buffer.");
        }
        return data;
    }

    /**
     * Read multiple bytes from buffer.
     *
     * @param buffer a byte arrary input stream
     * @param n number of bytes to be read
     *
     * @return an array of byte data read.
     *
     * @throws FormatException on out of data
     */
    private static byte[] readMultiBytes(ByteArrayInputStream buffer, int n)
        throws FormatException {
        byte[] data = new byte[n];
        if (buffer.read(data, 0, n) != n) {
            throw new FormatException("Not enough data in buffer.");
        }
        return data;
    }

    /**
     * Read a certificate from buffer.
     * The first two bytes are length of the certificate in big endian.
     *
     * @param buffer a byte arrary input stream
     *
     * @return an array of byte data read.
     *
     * @throws FormatException on out of data
     */
    private static byte[] readCertificate(ByteArrayInputStream buffer)
            throws FormatException {
        // Read certificate length
        int certLength = readByte(buffer) << 8;
        certLength |= readByte(buffer);
        // Read certificate
        byte[] certificate = readMultiBytes(buffer, certLength);
        return certificate;
    }

    /**
     * Read all data from URI.
     * Connect to the web site specified by URI and read all data available.
     *
     * @param uri web site address stored in a byte array
     *
     * @return an array of byte data read.
     *
     * @throws IOException on failure to connect to the web site and read data
     */
    private static byte[] readDataFromUri(byte[] uri) throws IOException {
        StringBuilder dataFromUri = new StringBuilder();
        URL url = new URL(new String(uri));
        URLConnection connection = url.openConnection();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream()));
        int inChar;
        while ((inChar = reader.read()) != -1) {
            dataFromUri.append((char)inChar);
        }
        reader.close();
        return dataFromUri.toString().getBytes();
    }

    /**
     * Verify NDEF message signature.
     *
     * @param msg NDEF message for signature verification
     *
     * @return NfcAdapter.SIGV_NO_SIGNATURE if not signed.
     *         NfcAdapter.SIGV_FAILED if failed.
     *         NfcAdapter.SIGV_PASSED if passed.
     *         NfcAdapter.SIGV_PASSED_WITHOUT_ROOT if passed without root certificate.
     *         NfcAdapter.SIGV_EXCEPTION if exception occured during process
     *
     * @throws CertificateException on parsing certificate errors.
     * @throws FormatException on parsing signature record errors
     * @throws InvalidKeyException on verifying signature errors
     * @throws IOException on retrieving signature or certificate errors from URI
     * @throws NoSuchAlgorithmException on verifying signature or certificate errors
     * @throws NoSuchProviderException on verifying signature or certificate errors
     * @throws SignatureException on verifying siganutre or certificate errors
     */
    public static int verify(NdefMessage msg)
        throws CertificateException, FormatException, InvalidKeyException, IOException,
               NoSuchAlgorithmException, NoSuchProviderException, SignatureException {
        boolean signed = false;
        NdefRecord[] records = msg.getRecords();

        for (NdefRecord r : records) {
            if (Arrays.equals(r.getType(), NdefRecord.RTD_SIGNATURE)) {
                // Found signature record
                signed = true;
                break;
            }
        }
        if (!signed) {
            return NfcAdapter.SIGV_NO_SIGNATURE;
        }

        // Signature exists
        boolean hasCaCert = false;
        ByteArrayOutputStream recordsRawData = new ByteArrayOutputStream();
        for (int i = 0; i < records.length; i++) {
            if (Arrays.equals(records[i].getType(), NdefRecord.RTD_SIGNATURE)) {
                // Found signature record, so verify signature for records from start to (i - 1). If
                // passed, continue, otherwise, just return SIGV_FAILED.
                ByteArrayInputStream buffer = new ByteArrayInputStream(records[i].getPayload());
                byte version = (byte)readByte(buffer); // Version
                if (version != NdefRecord.SIGNATURE_RTD_VERSION) {
                    throw new FormatException("Only Signature RTD 2.0 is Supported");
                }
                // URI_Present and Signature Type
                byte signatureHeader = (byte)readByte(buffer);
                boolean sigUriPresent = (signatureHeader & 0x80) != 0;
                byte signatureId = (byte)(signatureHeader & 0x7F);
                if (sigUriPresent &&
                    (signatureId == NdefSignatureType.NO_SIGNATURE_PRESENT.getId())) {
                    throw new FormatException(
                        "Signature type must be specified when URI_Present bit is 1.");
                }
                if (signatureId != NdefSignatureType.NO_SIGNATURE_PRESENT.getId()) {
                    byte hashId = (byte)readByte(buffer); // Hash Type
                    if (hashId != NdefHashType.SHA_256.getId()) {
                        throw new FormatException(
                            "Unknown hash type value: " + hashId + ".");
                    }
                    // Signature or URI Length in big endian
                    int sigUriLength = readByte(buffer) << 8;
                    sigUriLength |= readByte(buffer);
                    byte[] signature;
                    if (sigUriPresent) {
                        // Retrieve signature from URI
                        byte[] signatureUri = readMultiBytes(buffer, sigUriLength);
                        try {
                            signature = readDataFromUri(signatureUri);
                        } catch (IOException e) {
                            // Failed to get signature from URI, throw exception
                            throw new IOException(
                                "IOException on getting signature from URI.", e);
                        }
                    } else {
                        signature = readMultiBytes(buffer, sigUriLength);
                    }

                    // Retrieve certificate chain
                    // URI_Present flag and Cert_Format
                    byte certificateChainHeader = (byte)readByte(buffer);
                    boolean certUriPresent = (certificateChainHeader & 0x80) != 0;
                    byte certificateFormat = (byte)((certificateChainHeader & 0x70) >> 4);
                    int certificateCount = certificateChainHeader & 0x0F;
                    List<byte[]> certificateChain = new ArrayList<byte[]>();
                    int j;
                    byte[] rawCert;

                    for (j = 0; j < certificateCount; j++) {
                        // Read certificate and add to certificateChain
                        rawCert = readCertificate(buffer);
                        certificateChain.add(rawCert);
                    }
                    if (certUriPresent) {
                        // Certificate URI Length in big endian
                        int uriLength = readByte(buffer) << 8;
                        uriLength |= readByte(buffer);
                        byte[] certificateChainUri = readMultiBytes(buffer, uriLength);
                        // Retrieve certificates from URI, add to certificateChain
                        // and increase certificateCount
                        byte[] certsFromUri;
                        try {
                            certsFromUri = readDataFromUri(certificateChainUri);
                        } catch (IOException e) {
                            // Failed to get certificates from URI, throw exception
                            throw new IOException(
                                "IOException on getting certificates from URI.", e);
                        }
                        ByteArrayInputStream uriDataBuffer =
                            new ByteArrayInputStream(certsFromUri);
                        while (uriDataBuffer.available() > 0) {
                            // Read certificate, add to certificateChain and
                            // increase certificateCount
                            rawCert = readCertificate(uriDataBuffer);
                            certificateChain.add(rawCert);
                            certificateCount++;
                        }
                    }

                    if (!certificateChain.isEmpty()) {
                        // Pre-allocate an extra slot for CA certificate, but may not be used if the
                        // CA certificate can't be found
                        Certificate[] parsedCertChain = new Certificate[certificateCount + 1];
                        Certificate parsedCert;
                        try {
                            CertificateFactory factoryM2M =
                                CertificateFactory.getInstance(
                                    "M2M", M2mProvider.PROVIDER_NAME);
                            CertificateFactory factoryX509 =
                                CertificateFactory.getInstance(
                                    "X.509", BouncyCastleProvider.PROVIDER_NAME);

                            // Parse certificates
                            for (j = 0; j < certificateCount; j++) {
                                rawCert = certificateChain.get(j);
                                ByteArrayInputStream bIn = new ByteArrayInputStream(rawCert);
                                CertificateFactory factory;
                                byte tagNo = (byte)(rawCert[0] & 0x1F);
                                if (tagNo == M2mCertificate.APPLICATION_TAG_NUMBER) {
                                    // M2M Certificate
                                    factory = factoryM2M;
                                } else {
                                    // Try as an X509 Certificate
                                    factory = factoryX509;
                                }
                                parsedCertChain[j] = factory.generateCertificate(bIn);
                            }

                            // Find the CA cert from trusted certificate store based on the last
                            // certificate on the chain
                            parsedCert = parsedCertChain[certificateCount - 1];
                            // Set the CA cert slot to null in case no CA cert is found
                            parsedCertChain[certificateCount] = null;
                            TrustedCertificateStore certStore = new TrustedCertificateStore();
                            Certificate certForCA;
                            if (parsedCert instanceof M2mCertificate) {
                                // M2M Certificate
                                certForCA = ((M2mCertificate)parsedCert).getM2mCertificateLite();
                            } else {
                                // X509 Certificate
                                certForCA = parsedCert;
                            }
                            parsedCertChain[certificateCount] = certStore.findIssuer(certForCA);
                            if (parsedCertChain[certificateCount] != null) {
                                certificateCount++;
                                hasCaCert = true;
                            }

                            // Fill in inherited data
                            PublicKey curPublicKey;
                            SuperiorCertData inheritedData = new SuperiorCertData();
                            for (j = (certificateCount - 1); j >= 0; j--) {
                                if (parsedCertChain[j] instanceof M2mCertificate) {
                                    M2mCertificate curM2MCert = (M2mCertificate)parsedCertChain[j];

                                    curPublicKey = curM2MCert.getPublicKey();
                                    if (curPublicKey == null) {
                                        // Try to recreate it
                                        curPublicKey = curM2MCert.reconstructPublicKey(
                                            inheritedData.getPublicKey());
                                    }
                                    if (curPublicKey == null) {
                                        throw new FormatException(
                                            "M2M Certificate #" + j +
                                            " does not have a public key!");
                                    }

                                    if (curM2MCert.getIssuer() == null) {
                                        curM2MCert.setIssuer(inheritedData.getSubject());
                                    }

                                    if (curM2MCert.getCaKeyDefinition() == null) {
                                        curM2MCert.setCaKeyDefinition(
                                            inheritedData.getPublicKeyDefinition());
                                    }

                                    // Set inherited data
                                    inheritedData.setPublicKey(curPublicKey);
                                    inheritedData.setSubject(curM2MCert.getSubject());
                                    // It's possible that these values are being inherited from far
                                    // up the chain. If this cert's value happens to be null, we
                                    // don't want to inherit null going forward
                                    if (curM2MCert.getPublicKeyDefinition() != null) {
                                        inheritedData.setPublicKeyDefinition(
                                            curM2MCert.getPublicKeyDefinition());
                                    }
                                } else {
                                    X509Certificate curX509Cert =
                                        (X509Certificate)parsedCertChain[j];

                                    curPublicKey = curX509Cert.getPublicKey();
                                    if (curPublicKey == null) {
                                        throw new FormatException(
                                            "X509 Certificate #" + j +
                                            " does not have a public key!");
                                    }

                                    // Set inherited data
                                    // PublicKey
                                    inheritedData.setPublicKey(curPublicKey);
                                    // Subject
                                    X500Name x500Name = X500Name.getInstance(
                                        curX509Cert.getSubjectX500Principal().getEncoded());
                                    EntityName subject = new EntityName();
                                    int attributeCount = 0;

                                    for (RDN rdn : x500Name.getRDNs()) {
                                        AttributeTypeAndValue attr = rdn.getFirst();
                                        EntityNameAttributeId attributeId;

                                        if (attr.getType().equals(BCStyle.C)) {
                                            attributeId = EntityNameAttributeId.Country;
                                        } else if (attr.getType().equals(BCStyle.O)) {
                                            attributeId = EntityNameAttributeId.Organization;
                                        } else if (attr.getType().equals(BCStyle.OU)) {
                                            attributeId = EntityNameAttributeId.OrganizationalUnit;
                                        } else if (attr.getType().equals(BCStyle.DN_QUALIFIER)) {
                                            attributeId =
                                                EntityNameAttributeId.DistinguishedNameQualifier;
                                        } else if (attr.getType().equals(BCStyle.ST)) {
                                            attributeId = EntityNameAttributeId.StateOrProvince;
                                        } else if (attr.getType().equals(BCStyle.L)) {
                                            attributeId = EntityNameAttributeId.Locality;
                                        } else if (attr.getType().equals(BCStyle.CN)) {
                                            attributeId = EntityNameAttributeId.CommonName;
                                        } else if (attr.getType().equals(BCStyle.SN)) {
                                            attributeId = EntityNameAttributeId.SerialNumber;
                                        } else if (attr.getType().equals(BCStyle.DC)) {
                                            attributeId = EntityNameAttributeId.DomainComponent;
                                        } else {
                                            // Unsupported attribute.
                                            continue;
                                        }

                                        subject.addAttribute(
                                            new EntityNameAttribute(
                                                attributeId,
                                                IETFUtils.valueToString(attr.getValue())));
                                        attributeCount++;

                                        if (attributeCount == EntityName.MAXIMUM_ATTRIBUTES) {
                                            // We have reached the maximum number of attributes for
                                            // an EntityName, so stop here.
                                            break;
                                        }
                                    }
                                    if (attributeCount > 0) {
                                        inheritedData.setSubject(subject);
                                    } else {
                                        inheritedData.setSubject(null);
                                    }
                                    // PublicKey algorithm and parameters
                                    inheritedData.setPublicKeyDefinition(null);
                                }
                            }
                        } catch (CertificateException e) {
                            // Failed to get public key, throw exception
                            throw new CertificateException(
                                "CertificateException on getting public key.", e);
                        } catch (IOException e) {
                            // Failed to get public key, throw exception
                            throw new IOException(
                                "IOException on getting public key.", e);
                        }

                        PublicKey issuerPK;
                        // Verify signature
                        String sigTypeAlg =
                            NdefSignatureType.getInstanceOf(signatureId).getAlgorithm();
                        String hashType = NdefHashType.getInstanceOf(hashId).getName();

                        try {
                            // Verify signature with the first certificate which is
                            // the public key for signature verification
                            parsedCert = parsedCertChain[0];
                            issuerPK = parsedCert.getPublicKey();
                            if (issuerPK == null) {
                                // No public key, throw exception
                                throw new FormatException("No public key");
                            }

                            byte[] rawData = recordsRawData.toByteArray();
                            if (!verifySignature(rawData, sigTypeAlg, hashType,
                                                 signature, issuerPK)) {
                                // Signature is wrong, no need to check trusty
                                // of certificates
                                return NfcAdapter.SIGV_FAILED;
                            }
                        } catch (InvalidKeyException e) {
                            throw new InvalidKeyException(
                                "InvalidKeyException on verifying signature.", e);
                        } catch (IOException e) {
                            throw new IOException(
                                "IOException on verifying signature.", e);
                        } catch (NoSuchAlgorithmException e) {
                            throw new NoSuchAlgorithmException(
                                "NoSuchAlgorithmException on verifying signature.", e);
                        } catch (NoSuchProviderException e) {
                            throw new NoSuchProviderException(
                                "NoSuchProviderException on verifying signature. " + e);
                        } catch (SignatureException e) {
                            throw new SignatureException(
                                "SignatureException on verifying signature.", e);
                        }

                        // Check trust of the signature with certificates. The parsedCert has been
                        // set to parsedCertChain[0] above for verifying record signature, so we
                        // start the loop from 1.
                        for (j = 1; j < certificateCount; j++) {
                            // The parsedCert is the one for verification so store it in curCert
                            Certificate curCert = parsedCert;

                            try {
                                // Get public key from the next certificate to
                                // verify current certificate
                                parsedCert = parsedCertChain[j];

                                issuerPK = parsedCert.getPublicKey();
                                if (issuerPK == null) {
                                    // No public key, throw exception
                                    throw new FormatException("No public key");
                                }

                                // Verify certificate
                                // An exception is thrown out if it doesn't pass verification, and
                                // the type of exception indicates the reason
                                try {
                                    curCert.verify(issuerPK);
                                } catch (SignatureException e) {
                                    return NfcAdapter.SIGV_FAILED;
                                }
                                // Check certificate date
                                if (curCert instanceof M2mCertificate) {
                                    ((M2mCertificate)curCert).checkValidity();
                                } else {
                                    ((X509Certificate)curCert).checkValidity();
                                }
                            } catch (CertificateException e) {
                                throw new CertificateException(
                                    "CertificateException on verifying cerificates.", e);
                            } catch (InvalidKeyException e) {
                                throw new InvalidKeyException(
                                    "InvalidKeyException on verifying cerificates.", e);
                            } catch (NoSuchAlgorithmException e) {
                                throw new NoSuchAlgorithmException(
                                    "NoSuchAlgorithmException on verifying cerificates.", e);
                            } catch (NoSuchProviderException e) {
                                throw new NoSuchProviderException(
                                    "NoSuchProviderException on verifying cerificates. " + e);
                            }
                        }
                    } else {
                        throw new FormatException(
                            "No certificate in NDEF Signature record.");
                    }
                }

                // Reset data buffer for next segment of records
                recordsRawData.reset();
            } else {
                // Combine non-signature record raw data for verification
                ByteBuffer tmpBuffer = ByteBuffer.allocate(records[i].getByteLength());
                boolean mb = (i == 0);  // first record
                boolean me = (i == records.length - 1);  // last record
                records[i].writeToByteBuffer(tmpBuffer, mb, me);
                byte[] recordBytes = tmpBuffer.array();
                recordsRawData.write(recordBytes, 0, recordBytes.length);
            }
        }

        if (hasCaCert) {
            return NfcAdapter.SIGV_PASSED;
        } else {
            return NfcAdapter.SIGV_PASSED_WITHOUT_ROOT;
        }
    }
}
