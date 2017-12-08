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

package android.net.util;

import static android.net.MacAddress.ALL_ZEROS_ADDRESS;
import static android.net.util.NetworkConstants.ETHER_MTU;
import static android.net.util.NetworkConstants.IPV6_MIN_MTU;

import android.net.MacAddress;

import java.net.NetworkInterface;
import java.net.SocketException;


/**
 * Encapsulate the interface parameters common to IpClient/IpServer components.
 *
 * Basically all java.net.NetworkInterface methods throw Exceptions. IpClient
 * and IpServer (sub)components need most or all of this information at some
 * point during their lifecycles, so pass only this simplified object around
 * which can be created once when IpClient/IpServer are told to start.
 *
 * @hide
 */
public class InterfaceParams {
    public final String name;
    public final int index;
    public final MacAddress macAddr;
    public final int defaultMtu;

    public static InterfaceParams getByName(String name) {
        if (name == null) return null;

        try {
            final NetworkInterface netif = NetworkInterface.getByName(name);
            if (netif != null) {
                final MacAddress macAddr = netif.isLoopback()
                        ? null : MacAddress.fromBytes(netif.getHardwareAddress());
                return new InterfaceParams(
                        name, netif.getIndex(), macAddr, netif.getMTU());
            }
        } catch (IllegalArgumentException|SocketException e) {}

        return null;
    }

    public InterfaceParams(String name) {
        this(name, 0, null);
    }

    public InterfaceParams(String name, int index, MacAddress macAddr) {
        this(name, index, macAddr, ETHER_MTU);
    }

    public InterfaceParams(String name, int index, MacAddress macAddr, int defaultMtu) {
        this.name = name;
        this.index = (index > 0) ? index : 0;
        this.macAddr = (macAddr != null) ? macAddr : ALL_ZEROS_ADDRESS;
        this.defaultMtu = (defaultMtu > IPV6_MIN_MTU) ? defaultMtu : IPV6_MIN_MTU;
    }

    @Override
    public String toString() {
        return String.format("%s/%d/%s/%d", name, index, macAddr, defaultMtu);
    }
}
