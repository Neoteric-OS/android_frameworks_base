/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package android.net.wifi.hotspot2.pps;

import android.os.Parcelable;
import android.os.Parcel;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Class representing Credential subtree in the PerProviderSubscription (PPS)
 * Management Object (MO) tree.
 * For more info, refer to Hotspot 2.0 PPS MO defined in section 9.1 of the Hotspot 2.0
 * Release 2 Technical Specification.
 *
 * In addition to the fields in the Credential subtree, it will also maintain necessary
 * information for the private key and certificates associated with this credential.
 * 
 * @hide
 */
public final class Credential implements Parcelable {
    /**
     * The time this credential is created. It is in the format of number
     * of milliseconds since January 1, 1970, 00:00:00 GMT.
     */
    public long creationTime;

    /**
     * The time this credential will expired. It is in the format of number
     * of milliseconds since January 1, 1970, 00:00:00 GMT.
     */
    public long expirationTime;

    /**
     * The realm associated with this credential.  It is used to determine
     * if this credential can be used to authenticate with a given hotspot by
     * comparing the realm specified in that hotspot's ANQP element.
     */
    public String realm;

    /**
     * When set to true, the device should check AAA (Authentication, Authorization,
     * and Accounting) server's certificate during EAP (Extensible Authentication
     * Protocol) authentication.
     */
    public boolean checkAAAServerCertStatus;

    /**
     * Username-password based credential.
     * Contains the fields under PerProviderSubscription/Credential/UsernamePassword subtree.
     */
    public static final class UserCredential implements Parcelable {
        /**
         * Username of the credential. Maximum length is 63 octets.
         */
        public String username;

        /**
         * Base64-encoded password. Maximum length is 255 octets.
         */
        public String password;

        /**
         * Flag indicating if the password is machine managed.
         */
        public boolean machineManaged;

        /**
         * The name of the application should be used to generate the password.
         */
        public String softTokenApp;

        /**
         * Flag indicating if this credential is usable on other mobile devices as well.
         */
        public boolean ableToShare;

        /**
         * EAP (Extensible Authentication Protocol) method type.
         */
        public int eapType;

        /**
         * Information for Expanded EAP (Extensible Authentication Protocol) method.
         */
        public static final class ExpandedEAPMethod implements Parcelable {
            /**
             * Vendor-ID for an expanded EAP (Extensible Authentication Protocol) method.
             * Use object type of indicate this field as optional.
             */
            public final int eapVendorId;

            /**
             * Vendor-Type for an expanded EAP (Extensible Authentication Protocol) method.
             * Use object type of indicate this field as optional.
             */
            public final int eapVendorType;

            public ExpandedEAPMethod(int vendorId, int vendorType) {
                eapVendorId = vendorId;
                eapVendorType = vendorType;
            }

            /** Implement the Parcelable interface {@hide} */
            @Override
            public int describeContents() {
                return 0;
            }

            /** Implement the Parcelable interface {@hide} */
            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(eapVendorId);
                dest.writeInt(eapVendorType);
            }

            /** Implement the Parcelable interface {@hide} */
            public static final Creator<ExpandedEAPMethod> CREATOR =
                new Creator<ExpandedEAPMethod>() {
                    @Override
                    public ExpandedEAPMethod createFromParcel(Parcel in) {
                        int vendorId = in.readInt();
                        int vendorType = in.readInt();
                        return new ExpandedEAPMethod(vendorId, vendorType);
                    }

                    @Override
                    public ExpandedEAPMethod[] newArray(int size) {
                        return new ExpandedEAPMethod[size];
                    }
                };
        }
        public ExpandedEAPMethod expandedEapMethod;

        /**
         * Information for inner EAP (Extensible Authentication Protocol) method.
         */
        public static final class InnerEAPMethod implements Parcelable {
            /**
             * EAP (Extensible Authentication Protocol) type for an inner EAP method.
             */
            public final int eapInnerType;

            /**
             * Vendor-ID for an inner expanded EAP (Extensible Authentication Protocol) method.
             * Use object type of indicate this field as optional.
             */        
            public final int eapInnerVendorId;

            /**
             * Vendor-Type for an inner expanded EAP (Extensible Authentication Protocol) method.
             * Use object type of indicate this field as optional.
             */        
            public final int eapInnerVendorType;

            public InnerEAPMethod(int type, int vendorId, int vendorType) {
                eapInnerType = type;
                eapInnerVendorId = vendorId;
                eapInnerVendorType = vendorType;
            }

            /** Implement the Parcelable interface {@hide} */
            @Override
            public int describeContents() {
                return 0;
            }

            /** Implement the Parcelable interface {@hide} */
            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(eapInnerType);
                dest.writeInt(eapInnerVendorId);
                dest.writeInt(eapInnerVendorType);
            }

            /** Implement the Parcelable interface {@hide} */
            public static final Creator<InnerEAPMethod> CREATOR =
                new Creator<InnerEAPMethod>() {
                    @Override
                    public InnerEAPMethod createFromParcel(Parcel in) {
                        int eapType = in.readInt();
                        int vendorId = in.readInt();
                        int vendorType = in.readInt();
                        return new InnerEAPMethod(eapType, vendorId, vendorType);
                    }

                    @Override
                    public InnerEAPMethod[] newArray(int size) {
                        return new InnerEAPMethod[size];
                    }
                };
        }
        public InnerEAPMethod innerEapMethod;

        /**
         * Non-EAP inner authentication method. Valid values are "PAP", "CHAP", "MS-CHAP",
         * and "MS-CHAP-V2".
         */
        public String nonEapInnerMethod;

        public UserCredential() {
            username = null;
            password = null;
            machineManaged = false;
            softTokenApp = null;
            ableToShare = false;
            eapType = -1;
            expandedEapMethod = null;
            innerEapMethod = null;
            nonEapInnerMethod = null;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(username);
            dest.writeString(password);
            dest.writeInt(machineManaged ? 1 : 0);
            dest.writeString(softTokenApp);
            dest.writeInt(ableToShare ? 1 : 0);
            dest.writeInt(eapType);
            dest.writeParcelable(expandedEapMethod, flags);
            dest.writeParcelable(innerEapMethod, flags);
            dest.writeString(nonEapInnerMethod);
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<UserCredential> CREATOR =
            new Creator<UserCredential>() {
                @Override
                public UserCredential createFromParcel(Parcel in) {
                    UserCredential userCredential = new UserCredential();
                    userCredential.username = in.readString();
                    userCredential.password = in.readString();
                    userCredential.machineManaged = in.readInt() != 0;
                    userCredential.softTokenApp = in.readString();
                    userCredential.ableToShare = in.readInt() != 0;
                    userCredential.eapType = in.readInt();
                    userCredential.expandedEapMethod = in.readParcelable(null);
                    userCredential.innerEapMethod = in.readParcelable(null);
                    userCredential.nonEapInnerMethod = in.readString();
                    return userCredential;
                }

                @Override
                public UserCredential[] newArray(int size) {
                    return new UserCredential[size];
                }
            };
    }
    public UserCredential userCredential;

    /**
     * Certificate based credential.
     * Contains fields under PerProviderSubscription/Credential/DigitalCertificate subtree.
     */
    public static final class CertificateCredential implements Parcelable {
        /**
         * Certificate type. Valid values are "802.1ar" and "x509v3".
         */
        public String certType;

        /**
         * The SHA-256 fingerprint of the certificate.
         */
        public byte[] certSha256FingerPrint;

        public CertificateCredential() {
            certType = null;
            certSha256FingerPrint = null;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(certType);
            dest.writeByteArray(certSha256FingerPrint);
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<CertificateCredential> CREATOR =
            new Creator<CertificateCredential>() {
                @Override
                public CertificateCredential createFromParcel(Parcel in) {
                    CertificateCredential certCredential = new CertificateCredential();
                    certCredential.certType = in.readString();
                    certCredential.certSha256FingerPrint = in.createByteArray();
                    return certCredential;
                }

                @Override
                public CertificateCredential[] newArray(int size) {
                    return new CertificateCredential[size];
                }
            };
    }
    public CertificateCredential certCredential;

    /**
     * SIM (Subscriber Identify Module) based credential.
     * Contains fields under PerProviderSubscription/Credential/SIM subtree.
     */
    public static final class SimCredential implements Parcelable {
        /**
         * International Mobile device Subscriber Identity.
         */
        public String imsi;

        /**
         * EAP (Extensible Authentication Protocol) method type for using SIM credential.
         * Possible values are EAP-SIM, EAP-AKA, and EAP-AKAPrim.
         */
        public int eapType;

        public SimCredential() {
            imsi = null;
            eapType = -1;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(imsi);
            dest.writeInt(eapType);
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<SimCredential> CREATOR =
            new Creator<SimCredential>() {
                @Override
                public SimCredential createFromParcel(Parcel in) {
                    SimCredential simCredential = new SimCredential();
                    simCredential.imsi = in.readString();
                    simCredential.eapType = in.readInt();
                    return simCredential;
                }

                @Override
                public SimCredential[] newArray(int size) {
                    return new SimCredential[size];
                }
            };
    }
    public SimCredential simCredential;

    /**
     * CA (Certificate Authority) X509 certificate.
     */
    public X509Certificate caCertificate;

    /**
     * Client side X509 certificate chain.
     */
    public X509Certificate[] clientCertifcateChain;

    /**
     * Client side private key.
     */
    public PrivateKey clientPrivateKey;

    public Credential() {
        creationTime = -1;
        expirationTime = -1;
        realm = null;
        checkAAAServerCertStatus = false;
        userCredential = null;
        certCredential = null;
        simCredential = null;
        caCertificate = null;
        clientCertifcateChain = null;
        clientPrivateKey = null;
    }

    /** Implement the Parcelable interface {@hide} */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(creationTime);
        dest.writeLong(expirationTime);
        dest.writeString(realm);
        dest.writeInt(checkAAAServerCertStatus ? 1 : 0);
        dest.writeParcelable(userCredential, flags);
        dest.writeParcelable(certCredential, flags);
        dest.writeParcelable(simCredential, flags);
        writeCertificate(dest, caCertificate);
        writeCertificates(dest, clientCertifcateChain);
        writePrivateKey(dest, clientPrivateKey);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<Credential> CREATOR =
        new Creator<Credential>() {
            @Override
            public Credential createFromParcel(Parcel in) {
                Credential credential = new Credential();
                credential.creationTime = in.readLong();
                credential.expirationTime = in.readLong();
                credential.realm = in.readString();
                credential.checkAAAServerCertStatus = in.readInt() != 0;
                credential.userCredential = in.readParcelable(null);
                credential.certCredential = in.readParcelable(null);
                credential.simCredential = in.readParcelable(null);
                credential.caCertificate = readCertificate(in);
                credential.clientCertifcateChain = readCertificates(in);
                credential.clientPrivateKey = readPrivateKey(in);
                return credential;
            }

            @Override
            public Credential[] newArray(int size) {
                return new Credential[size];
            }

            private PrivateKey readPrivateKey(Parcel in) {
                PrivateKey key = null;
                int len = in.readInt();
                if (len > 0) {
                    try {
                        byte[] bytes = new byte[len];
                        in.readByteArray(bytes);
                        String algorithm = in.readString();
                        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                        key = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        key = null;
                    }
                }
                return key;
            }

            private X509Certificate[] readCertificates(Parcel in) {
                X509Certificate[] certs = null;
                int len = in.readInt();
                if (len > 0) {
                    certs = new X509Certificate[len];
                    for (int i = 0; i < len; i++) {
                        certs[i] = readCertificate(in);
                    }
                }
                return certs;
            }

            private X509Certificate readCertificate(Parcel in) {
                X509Certificate cert = null;
                int len = in.readInt();
                if (len > 0) {
                    try {
                        byte[] bytes = new byte[len];
                        in.readByteArray(bytes);
                        CertificateFactory cFactory = CertificateFactory.getInstance("X.509");
                        cert = (X509Certificate) cFactory
                                .generateCertificate(new ByteArrayInputStream(bytes));
                    } catch (CertificateException e) {
                        cert = null;
                    }
                }
                return cert;
            }
        };

    private static void writePrivateKey(Parcel dest, PrivateKey key) {
        if (key != null) {
            String algorithm = key.getAlgorithm();
            byte[] userKeyBytes = key.getEncoded();
            dest.writeInt(userKeyBytes.length);
            dest.writeByteArray(userKeyBytes);
            dest.writeString(algorithm);
        } else {
            dest.writeInt(0);
        }
    }

    private static void writeCertificates(Parcel dest, X509Certificate[] cert) {
        if (cert != null && cert.length != 0) {
            dest.writeInt(cert.length);
            for (int i = 0; i < cert.length; i++) {
                writeCertificate(dest, cert[i]);
            }
        } else {
            dest.writeInt(0);
        }
    }

    private static void writeCertificate(Parcel dest, X509Certificate cert) {
        if (cert != null) {
            try {
                byte[] certBytes = cert.getEncoded();
                dest.writeInt(certBytes.length);
                dest.writeByteArray(certBytes);
            } catch (CertificateEncodingException e) {
                dest.writeInt(0);
            }
        } else {
            dest.writeInt(0);
        }
    }

}

