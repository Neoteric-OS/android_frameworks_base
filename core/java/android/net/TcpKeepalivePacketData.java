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
import android.net.util.IpUtils;
import android.system.OsConstants;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents the actual tcp keep alive packets which will be used for hardware offload.
 * @hide
 */

public class TcpKeepalivePacketData extends KeepalivePacketData {
    private static final String TAG = "TcpKeepalivePacketData";

     /** TCP sequence number. */
    public final int tcpSeq;

    /** TCP ACK number. */
    public final int tcpAck;

    private static final int IPV4_HEADER_LENGTH = 20;
    private static final int IPV6_HEADER_LENGTH = 40;
    private static final int TCP_HEADER_LENGTH = 20;

    // This should only be constructed via static factory methods, such as
    // tcpKeepalivePacket.
    private TcpKeepalivePacketData(TcpSocketInfo tcpDetails, byte[] data)
            throws InvalidPacketException {
        super(tcpDetails.srcAddress, tcpDetails.srcPort, tcpDetails.dstAddress,
                tcpDetails.dstPort, data);
        tcpSeq = tcpDetails.seq;
        tcpAck = tcpDetails.ack;
    }

    /**
     * Factory method to create tcp keepalive packet structure.
     */
    public static TcpKeepalivePacketData tcpKeepalivePacket(
            TcpSocketInfo tcpDetails) throws InvalidPacketException {
        final byte[] packet;
        if ((tcpDetails.srcAddress instanceof Inet4Address)
                && (tcpDetails.dstAddress instanceof Inet4Address)) {
            packet = buildV4Packet(tcpDetails);
        } else {
            // TODO: support ipv6
            packet = null;
            throw new InvalidPacketException(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        }

        return new TcpKeepalivePacketData(tcpDetails, packet);
    }

    /**
     * Build ipv4 tcp keepalive packet, not including the link-layer header.
     */
    private static byte[] buildV4Packet(TcpSocketInfo tcpDetails) {
        final int length = IPV4_HEADER_LENGTH + TCP_HEADER_LENGTH;
        ByteBuffer buf = ByteBuffer.allocate(length);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) 0x4500);               // IP version and TODO: TOS
        buf.putShort((short) length);
        buf.putInt((int) 0x400);                    // ID, flags=DF, offset
        buf.put((byte) 64);                         // TODO: TTL
        buf.put((byte) OsConstants.IPPROTO_TCP);
        final int ipChecksumOffset = buf.position();
        buf.putShort((short) 0);                    // IP checksum
        buf.put(tcpDetails.srcAddress.getAddress());
        buf.put(tcpDetails.dstAddress.getAddress());
        buf.putShort((short) tcpDetails.srcPort);
        buf.putShort((short) tcpDetails.dstPort);
        buf.putInt(tcpDetails.seq);                 // Sequence Number
        buf.putInt(tcpDetails.ack);                 // ACK
        buf.putShort((short) 0x5010);               // TCP length=5, flags=ACK
        buf.putShort((short) tcpDetails.rcv_wnd);   // Window size
        final int tcpChecksumOffset = buf.position();
        buf.putShort((short) 0);                    // TCP checksum
        buf.putShort(ipChecksumOffset, IpUtils.ipChecksum(buf, 0));
        buf.putShort(tcpChecksumOffset, IpUtils.tcpChecksum(
                buf, 0, IPV4_HEADER_LENGTH, TCP_HEADER_LENGTH));

        return buf.array();
    }

    // TODO: add buildV6Packet.

    /** Represents tcp/ip infromation. */
    // TODO: Replace TcpSocketInfo with TcpKeepalivePacketDataParcelable.
    public static class TcpSocketInfo {
        public final InetAddress srcAddress;
        public final InetAddress dstAddress;
        public final int srcPort;
        public final int dstPort;
        public final int seq;
        public final int ack;
        public final int rcv_wnd;

        public TcpSocketInfo(InetAddress sAddr, int sPort, InetAddress dAddr,
                int dPort, int writeSeq, int readSeq, int rWnd) {
            srcAddress = sAddr;
            dstAddress = dAddr;
            srcPort = sPort;
            dstPort = dPort;
            seq = writeSeq;
            ack = readSeq;
            rcv_wnd = rWnd;
        }
    }

    /**
     * Convert this TcpKeepalivePacketData to a TcpKeepalivePacketDataParcelable.
     */
    @NonNull
    public TcpKeepalivePacketDataParcelable toParcelable() {
        final TcpKeepalivePacketDataParcelable parcel = new TcpKeepalivePacketDataParcelable();
        parcel.srcAddress = srcAddress.getHostAddress();
        parcel.dstAddress = dstAddress.getHostAddress();
        parcel.srcPort = srcPort;
        parcel.dstPort = dstPort;
        parcel.seqNum = tcpSeq;
        parcel.ackNum = tcpAck;
        return parcel;
    }

   /**
     * Convert a TcpKeepalivePacketDataParcelable to a TcpKeepalivePacketData.
     */
    public static TcpKeepalivePacketData fromStableParcelable(
            @Nullable TcpKeepalivePacketDataParcelable parcel) {
        if (null == parcel) return null;
        TcpKeepalivePacketData pkt = null;
        try {
            final TcpSocketInfo tcpInfo =
                    new TcpSocketInfo(InetAddresses.parseNumericAddress(parcel.srcAddress),
                            parcel.srcPort,
                            InetAddresses.parseNumericAddress(parcel.dstAddress),
                            parcel.dstPort, parcel.seqNum, parcel.ackNum);
            pkt = tcpKeepalivePacket(tcpInfo);
        } catch (InvalidPacketException e) {
            Log.e(TAG, "fromStableParcelable: " + e);
        }
        return pkt;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("saddr: " + srcAddress)
                .append(" daddr: " + dstAddress)
                .append(" sport: " + srcPort)
                .append(" dport: " + dstPort)
                .append(" seq: " + tcpAck)
                .append(" ack: " + tcpAck);
        return sb.toString();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!(o instanceof TcpKeepalivePacketData)) {
            return false;
        }
        final TcpKeepalivePacketData other = (TcpKeepalivePacketData) o;
        return this.srcAddress.equals(other.srcAddress)
                && this.dstAddress.equals(other.dstAddress)
                && this.srcPort == other.srcPort
                && this.dstPort == other.dstPort
                && this.tcpAck == other.tcpAck
                && this.tcpSeq == other.tcpSeq;
    }
}
