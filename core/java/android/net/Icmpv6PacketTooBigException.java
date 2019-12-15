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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.system.OsConstants;

import java.net.InetAddress;

/**
 * Icmpv6PacketTooBigException represents an ICMP6_PACKET_TOO_BIG error. This Exception is thrown
 * when an MTU smaller than the transmitted ICMPv6 packet is encountered in the network.
 *
 * @see <a href="https://www.iana.org/assignments/icmpv6-parameters/icmpv6-parameters.xhtml
 *     #icmpv6-parameters-codes-3">ICMPv6 Packet Too Big</a>
 */
public class Icmpv6PacketTooBigException extends Icmpv6Exception {
    @NonNull public final InetAddress address;
    public final int mtu;

    /**
     * Constructs a Icmpv6PacketTooBigException instance.
     *
     * @param address The InetAddress that reported the PACKET_TOO_BIG error
     * @param mtu The MTU (in bytes) reported returned by address
     */
    public Icmpv6PacketTooBigException(@NonNull InetAddress address, int mtu) {
        this(address, mtu, "");
    }

    /**
     * Constructs a Icmpv6PacketTooBigException instance.
     *
     * @param address The InetAddress that reported the PACKET_TOO_BIG error
     * @param mtu The MTU (in bytes) reported returned by address
     * @param msg The message to be used for this Exception
     */
    public Icmpv6PacketTooBigException(
            @NonNull InetAddress address, int mtu, @Nullable String msg) {
        this(address, mtu, msg, null);
    }

    /**
     * Constructs a Icmpv6PacketTooBigException instance.
     *
     * @param address The InetAddress that reported the PACKET_TOO_BIG error
     * @param mtu The MTU (in bytes) reported returned by address
     * @param msg The message to be used for this Exception
     * @param cause The Throwable that caused this Exception to be thrown
     */
    public Icmpv6PacketTooBigException(
            @NonNull InetAddress address,
            int mtu,
            @Nullable String msg,
            @Nullable Throwable cause) {
        super(OsConstants.ICMP6_PACKET_TOO_BIG, 0, msg, null);
        this.address = address;
        this.mtu = mtu;
    }
}
