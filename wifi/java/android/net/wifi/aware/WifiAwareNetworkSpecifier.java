/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.wifi.aware;

import android.net.NetworkSpecifier;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Network specifier object used to request a Wi-Fi Aware network. Apps do not create these objects
 * directly but obtain them using
 * {@link WifiAwareSession#createNetworkSpecifierOpen(int, byte[])} or
 * {@link DiscoverySession#createNetworkSpecifierOpen(PeerHandle)} or their secure (Passphrase)
 * versions.
 *
 * @hide
 */
public final class WifiAwareNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    /**
     * TYPE: in band, specific peer: role, client_id, session_id, peer_id, pmk/passphrase optional
     */
    public static final int NETWORK_SPECIFIER_TYPE_IB = 0;

    /**
     * TYPE: in band, any peer: role, client_id, session_id, pmk/passphrase optional
     * [only permitted for RESPONDER]
     */
    public static final int NETWORK_SPECIFIER_TYPE_IB_ANY_PEER = 1;

    /**
     * TYPE: out-of-band: role, client_id, peer_mac, pmk/passphrase optional
     */
    public static final int NETWORK_SPECIFIER_TYPE_OOB = 2;

    /**
     * TYPE: out-of-band, any peer: role, client_id, pmk/passphrase optional
     * [only permitted for RESPONDER]
     */
    public static final int NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER = 3;

    public static final int NETWORK_SPECIFIER_TYPE_MAX_VALID = NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER;

    /**
     * One of the NETWORK_SPECIFIER_TYPE_* constants. The type of the network specifier object.
     */
    public int type;

    /**
     * The role of the device: WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR or
     * WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER.
     */
    public int role;

    /**
     * The client ID of the device.
     */
    public int clientId;

    /**
     * The session ID in which context to request a data-path. Only relevant for IB requests.
     */
    public int sessionId;

    /**
     * The peer ID of the device which the data-path should be connected to. Only relevant for
     * IB requests (i.e. not IB_ANY_PEER or OOB*).
     */
    public int peerId;

    /**
     * The peer MAC address of the device which the data-path should be connected to. Only relevant
     * for OB requests (i.e. not OOB_ANY_PEER or IB*).
     */
    public byte[] peerMac;

    /**
     * The PMK of the requested data-path. Can be null. Only one or none of pmk or passphrase should
     * be specified.
     */
    public byte[] pmk;

    /**
     * The Passphrase of the requested data-path. Can be null. Only one or none of the pmk or
     * passphrase should be specified.
     */
    public String passphrase;

    public static final Creator<WifiAwareNetworkSpecifier> CREATOR =
            new Creator<WifiAwareNetworkSpecifier>() {
                @Override
                public WifiAwareNetworkSpecifier createFromParcel(Parcel in) {
                    WifiAwareNetworkSpecifier ns = new WifiAwareNetworkSpecifier();
                    ns.type = in.readInt();
                    ns.role = in.readInt();
                    ns.clientId = in.readInt();
                    ns.sessionId = in.readInt();
                    ns.peerId = in.readInt();
                    ns.peerMac = in.createByteArray();
                    ns.pmk = in.createByteArray();
                    ns.passphrase = in.readString();
                    return ns;
                }

                @Override
                public WifiAwareNetworkSpecifier[] newArray(int size) {
                    return new WifiAwareNetworkSpecifier[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeInt(role);
        dest.writeInt(clientId);
        dest.writeInt(sessionId);
        dest.writeInt(peerId);
        dest.writeByteArray(peerMac);
        dest.writeByteArray(pmk);
        dest.writeString(passphrase);
    }

    @Override
    public boolean satisfiedBy(NetworkSpecifier other) {
        // MatchAllNetworkSpecifier is taken care in NetworkCapabilities#satisfiedBySpecifier.
        return equals(other);
    }

    @Override
    public int hashCode() {
        int result = 17;

        result = 31 * result + type;
        result = 31 * result + role;
        result = 31 * result + clientId;
        result = 31 * result + sessionId;
        result = 31 * result + peerId;
        result = 31 * result + Arrays.hashCode(peerMac);
        result = 31 * result + Arrays.hashCode(pmk);
        result = 31 * result + Objects.hashCode(passphrase);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof WifiAwareNetworkSpecifier)) {
            return false;
        }

        WifiAwareNetworkSpecifier lhs = (WifiAwareNetworkSpecifier) obj;

        return type == lhs.type
                && role == lhs.role
                && clientId == lhs.clientId
                && sessionId == lhs.sessionId
                && peerId == lhs.peerId
                && Arrays.equals(peerMac, lhs.peerMac)
                && Arrays.equals(pmk, lhs.pmk)
                && Objects.equals(passphrase, lhs.passphrase);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WifiAwareNetworkSpecifier [");
        sb.append("type=").append(type)
                .append(", role=").append(role)
                .append(", clientId=").append(clientId)
                .append(", sessionId=").append(sessionId)
                .append(", peerId=").append(peerId)
                // masking potential PII (although low impact information)
                .append(", peerMac=").append((peerMac == null) ? "<null>" : "<non-null>")
                // masking PII
                .append(", pmk=").append((pmk == null) ? "<null>" : "<non-null>")
                // masking PII
                .append(", passphrase=").append((passphrase == null) ? "<null>" : "<non-null>")
                .append("]");
        return sb.toString();
    }
}
