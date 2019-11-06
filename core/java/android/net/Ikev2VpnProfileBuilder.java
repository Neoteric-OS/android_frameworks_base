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

package android.net;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.security.Credentials;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.net.VpnProfile;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

/**
 * Incrementally builds and validates {@link VpnProfile} instances for use in IKEv2/IPsec VPNs
 *
 * @see <a href="https://tools.ietf.org/html/rfc7296#section-3.3.2">RFC 7296 - Internet Key
 *     Exchange, Version 2 (IKEv2)</a>
 */
public final class Ikev2VpnProfileBuilder {
    private final Context mContext;

    /** @hide */
    @VisibleForTesting public final VpnProfile mProfile;

    /**
     * Creates a new builder with the basic parameters of an IKEv2/IPsec VPN
     *
     * @param context a valid Android Context
     * @param serverAddr the server that the VPN should connect to
     * @param identity the identity string to be used for IKEv2 authentication
     */
    public Ikev2VpnProfileBuilder(
            @NonNull Context context, @NonNull String serverAddr, @NonNull String identity) {
        checkNotNull(context, "Required parameter was null: context");
        checkNotNull(serverAddr, "Required parameter was null: serverAddr");
        checkNotNull(identity, "Required parameter was null: identity");

        mContext = context;
        mProfile = new VpnProfile("PlatformVpn_" + mContext.getOpPackageName());
        mProfile.server = serverAddr;
        mProfile.ipsecIdentifier = identity;
    }

    /**
     * Creates a new builder with the basic parameters of an IKEv2/IPsec VPN
     *
     * @param context a valid Android Context
     * @param serverAddr the server that the VPN should connect to
     * @param identity the identity string to be used for IKEv2 authentication
     */
    public Ikev2VpnProfileBuilder(
            @NonNull Context context, @NonNull InetAddress serverAddr, @NonNull String identity) {
        this(context, serverAddr.getHostAddress(), identity);
    }

    /**
     * Set the name of this session.
     *
     * <p>The session name will be displayed in system-managed dialogs and notifications. This is
     * recommended, but not required.
     *
     * @param name the name of this session, for use in system dialogs.
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setSessionName(@NonNull String name) {
        checkNotNull(name, "Required parameter was null: name");

        mProfile.name = name;
        return this;
    }

    @NonNull
    private Ikev2VpnProfileBuilder setUserCert(@NonNull X509Certificate cert)
            throws IOException, CertificateEncodingException {
        checkNotNull(cert, "Required parameter was null: cert");

        // Credentials.convertToPem outputs Base64 ASCII bytes.
        mProfile.ipsecUserCert =
                new String(Credentials.convertToPem(cert), StandardCharsets.US_ASCII);
        return this;
    }

    @NonNull
    private Ikev2VpnProfileBuilder setServerRootCa(@NonNull X509Certificate cert)
            throws IOException, CertificateEncodingException {
        checkNotNull(cert, "Required parameter was null: cert");

        // Credentials.convertToPem outputs Base64 ASCII bytes.
        mProfile.ipsecCaCert =
                new String(Credentials.convertToPem(cert), StandardCharsets.US_ASCII);
        return this;
    }

    /**
     * Set the IKEv2 authentication to use the provided username/password
     *
     * <p>Setting this will configure IKEv2 authentication using EAP-MSCHAPv2. Only one
     * authentication method may be selected.
     *
     * @param user the username to be used for EAP-MSCHAPv2 authentication
     * @param pass the password to be used for EAP-MSCHAPv2 authentication
     * @param serverRootCa the root certificate to be used for verifying the identity of the server
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setAuthUsernamePassword(
            @NonNull String user, @NonNull String pass, @NonNull X509Certificate serverRootCa)
            throws IOException, CertificateEncodingException {
        checkNotNull(user, "Required parameter was null: user");
        checkNotNull(pass, "Required parameter was null: pass");
        checkNotNull(serverRootCa, "Required parameter was null: serverRootCa");

        setServerRootCa(serverRootCa);
        mProfile.username = user;
        mProfile.password = pass;
        mProfile.type = VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS;
        mProfile.authParamsInline = true;
        return this;
    }

    /**
     * Set the IKEv2 authentication to use Digital Signature Authentication with the given key
     *
     * <p>Setting this will configure IKEv2 authentication using a Digital Signature scheme. Only
     * one authentication method may be selected.
     *
     * @param userCert the username to be used for EAP-MSCHAPv2 authentication
     * @param key the PrivateKey instance associated with the user ceritificate, used for
     *     constructing the signature
     * @param serverRootCa the root certificate to be used for verifying the identity of the server
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setAuthDigitalSignature(
            @NonNull X509Certificate userCert,
            @NonNull PrivateKey key,
            @NonNull X509Certificate serverRootCa)
            throws IOException, CertificateEncodingException {
        checkNotNull(userCert, "Required parameter was null: userCert");
        checkNotNull(key, "Required parameter was null: key");
        checkNotNull(serverRootCa, "Required parameter was null: serverRootCa");

        setServerRootCa(serverRootCa);
        setUserCert(userCert);
        mProfile.ipsecSecret = Base64.getEncoder().encodeToString(key.getEncoded());
        mProfile.type = VpnProfile.TYPE_IKEV2_IPSEC_RSA;
        mProfile.authParamsInline = true;
        return this;
    }

    /**
     * Set the IKEv2 authentication to use Preshared keys
     *
     * <p>Setting this will configure IKEv2 authentication using a Preshared Key. Only one
     * authentication method may be selected.
     *
     * @param psk the ASCII key to be used for EAP-MSCHAPv2 authentication
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setAuthPsk(@NonNull String psk) {
        checkNotNull(psk, "Required parameter was null: name");

        mProfile.ipsecSecret = psk;
        mProfile.type = VpnProfile.TYPE_IKEV2_IPSEC_PSK;
        mProfile.authParamsInline = true;
        return this;
    }

    /**
     * Allow all apps to bypass this VPN connection.
     *
     * <p>By default, all traffic from apps is forwarded through the VPN interface and it is not
     * possible for apps to side-step the VPN. If this method is called, apps may use methods such
     * as {@link ConnectivityManager#bindProcessToNetwork} to instead send/receive directly over the
     * underlying network or any other network they have permissions for.
     *
     * @param isBypassable Whether or not the VPN should be considered bypassable. Defaults to
     *     {@code false}.
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setBypassable(boolean isBypassable) {
        mProfile.isBypassable = isBypassable;
        return this;
    }

    /**
     * Sets an HTTP proxy for the VPN network.
     *
     * <p>Note that this proxy is only a recommendation and it may be ignored by apps.
     *
     * @param proxy the ProxyInfo to be set for the VPN network
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setHttpProxy(@Nullable ProxyInfo proxy) {
        mProfile.proxy = proxy;
        return this;
    }

    /**
     * Set the upper bound of the maximum transmission unit (MTU) of the VPN interface.
     *
     * <p>If it is not set, a safe value will be used. Additionally, the actual link MTU will be
     * dynamically calculated/updated based on the underlying link's mtu.
     *
     * @param mtu the MTU (in bytes) of the VPN interface.
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     * @throws IllegalArgumentException if the value is not at least greater than the minimum IPv6
     *     MTU.
     */
    @NonNull
    public Ikev2VpnProfileBuilder setMaxMtu(int mtu) {
        if (mtu <= 1280) {
            throw new IllegalArgumentException("MTU must be greater than 1280");
        }
        mProfile.maxMtu = mtu;
        return this;
    }

    /**
     * Marks the VPN network as metered.
     *
     * <p>A VPN network is classified as metered when the user is sensitive to heavy data usage due
     * to monetary costs and/or data limitations. In such cases, you should set this to {@code true}
     * so that apps on the system can avoid doing large data transfers. Otherwise, set this to
     * {@code false}. Doing so would cause VPN network to inherit its meteredness from the
     * underlying network.
     *
     * @param isMetered {@code true} if the VPN network should be treated as metered regardless of
     *     underlying network meteredness
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     * @see ConnectivityManager#isActiveNetworkMetered()
     */
    @NonNull
    public Ikev2VpnProfileBuilder setMetered(boolean isMetered) {
        mProfile.isMetered = isMetered;
        return this;
    }

    /**
     * Sets the allowable set of IPsec algorithms
     *
     * <p>A list of allowed IPsec algorithms as defined in {@link IpSecAlgorithm}
     *
     * @param algorithmNames the list of supported IPsec algorithms
     * @return this {@link Ikev2VpnProfileBuilder} object to facilitate chaining of method calls.
     * @see IpSecAlgorithm
     */
    @NonNull
    public Ikev2VpnProfileBuilder setAllowedAlgorithms(@NonNull List<String> algorithmNames) {
        checkNotNull(algorithmNames, "Required parameter was null: algorithmNames");

        mProfile.allowedAlgorithms = algorithmNames;
        return this;
    }

    /**
     * Validates, builds and provisions the VpnProfile.
     *
     * @returns an Intent requesting user consent to start the VPN, or null otherwise
     * @throws GeneralSecurityException if any of the required keys or values were invalid.
     */
    @NonNull
    public Intent build() throws GeneralSecurityException {
        mProfile.validateIkev2Ipsec();

        final VpnProfile result = new VpnProfile(mProfile);
        return mContext.getSystemService(VpnManager.class).provisionVpn(result);
    }
}
