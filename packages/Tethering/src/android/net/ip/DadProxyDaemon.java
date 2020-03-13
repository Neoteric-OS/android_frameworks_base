/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.ip;

import android.net.ip.NeighborPacketForwarder.PacketType;
import android.net.util.InterfaceParams;

import android.os.Handler;

public class DadProxyDaemon {
    private static final String TAG = DadProxyDaemon.class.getSimpleName();

    private static NeighborPacketForwarder naForwarder, nsForwarder;

    public DadProxyDaemon(Handler h, InterfaceParams tetheredIface) {
        naForwarder = new NeighborPacketForwarder(h, tetheredIface, PacketType.ADVERT);
        nsForwarder = new NeighborPacketForwarder(h, tetheredIface, PacketType.SOLICIT);
    }

    public void start() {
        // The forwarders independently start themselves
    }

    public void stop() {
        naForwarder.stop();
        nsForwarder.stop();
    }

    public void setUpstreamIface(String upstreamIface) {
        naForwarder.setUpstreamIface(upstreamIface);
        nsForwarder.setUpstreamIface(upstreamIface);
    }
}