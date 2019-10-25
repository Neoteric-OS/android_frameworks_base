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

import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;
import static android.net.DnsResolver.TYPE_CNAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.BitUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Defines basic data for DNS protocol based on RFC 1035.
 * Subclasses create the specific format used in DNS packet.
 *
 * @hide
 */
public abstract class DnsPacket {
    public static class DnsHeader {
        private static final String TAG = "DnsHeader";
        public static final int SIZE = 12;
        public final int id;
        public final int flags;
        public final int rcode;
        private final int[] mRecordCount;

        /**
         * Create a new DnsHeader from a positioned ByteBuffer.
         *
         * The ByteBuffer must be in network byte order (which is the default).
         * Reads the passed ByteBuffer from its current position and decodes a DNS header.
         * When this constructor returns, the reading position of the ByteBuffer has been
         * advanced to the end of the DNS header record.
         * This is meant to chain with other methods reading a DNS response in sequence.
         */
        @VisibleForTesting
        protected DnsHeader(@NonNull ByteBuffer buf) throws BufferUnderflowException {
            id = BitUtils.uint16(buf.getShort());
            flags = BitUtils.uint16(buf.getShort());
            rcode = flags & 0xF;
            mRecordCount = new int[NUM_SECTIONS];
            for (int i = 0; i < NUM_SECTIONS; ++i) {
                mRecordCount[i] = BitUtils.uint16(buf.getShort());
            }
        }

        /**
         * Create a new DnsHeader from specified parameters, useful when synthesize dns response
         * from query packet.
         */
        public DnsHeader(int id, int flags, int qcount, int anscount) {
            this.id = id;
            this.flags = flags;
            this.rcode = flags & 0xF;
            mRecordCount = new int[NUM_SECTIONS];
            mRecordCount[QDSECTION] = qcount;
            mRecordCount[ANSECTION] = anscount;
        }

        /**
         * Get record count by type.
         */
        public int getRecordCount(int type) {
            return mRecordCount[type];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;
            DnsHeader dnsHeader = (DnsHeader) o;
            return id == dnsHeader.id
                    && flags == dnsHeader.flags
                    && rcode == dnsHeader.rcode
                    && Arrays.equals(mRecordCount, dnsHeader.mRecordCount);
        }

        public @NonNull byte[] getBytes() {
            final ByteBuffer buf = ByteBuffer.allocate(SIZE);
            buf.putShort((short) id);
            buf.putShort((short) flags);
            for (int i = 0; i < NUM_SECTIONS; ++i) {
                buf.putShort((short) mRecordCount[i]);
            }
            return buf.array();
        }
    }

    /**
     * Superclass for DNS questions and DNS resource records.
     *
     * DNS questions (No TTL/RDATA)
     * DNS resource records (With TTL/RDATA)
     */
    public static class DnsRecord {
        private static final int MAXNAMESIZE = 255;
        private static final int MAXLABELSIZE = 63;
        private static final int MAXLABELCOUNT = 128;
        private static final int NAME_NORMAL = 0;
        private static final int NAME_COMPRESSION = 0xC0;
        private final DecimalFormat byteFormat = new DecimalFormat();
        private final FieldPosition pos = new FieldPosition(0);

        private static final String TAG = "DnsRecord";

        public final int rType;
        public final String dName;
        public final int nsType;
        public final int nsClass;
        public final long ttl;
        private final byte[] mRdata;

        /**
         * Create a new DnsRecord from a positioned ByteBuffer.
         *
         * Reads the passed ByteBuffer from its current position and decodes a DNS record.
         * When this constructor returns, the reading position of the ByteBuffer has been
         * advanced to the end of the DNS header record.
         * This is meant to chain with other methods reading a DNS response in sequence.
         *
         * @param ByteBuffer input of record, must be in network byte order
         *                   (which is the default).
         */
        @VisibleForTesting
        protected DnsRecord(int rType, @NonNull ByteBuffer buf)
                throws BufferUnderflowException, ParseException {
            this.rType = rType;
            dName = parseName(buf, 0 /* Parse depth */);
            if (dName.length() > MAXNAMESIZE) {
                throw new ParseException(
                        "Parse name fail, name size is too long: " + dName.length());
            }
            nsType = BitUtils.uint16(buf.getShort());
            nsClass = BitUtils.uint16(buf.getShort());

            if (rType != QDSECTION) {
                ttl = BitUtils.uint32(buf.getInt());
                final int length = BitUtils.uint16(buf.getShort());
                mRdata = new byte[length];
                buf.get(mRdata);
            } else {
                ttl = 0;
                mRdata = null;
            }
        }

        /**
         * Create a new DnsRecord from specified parameters, useful when synthesize dns response.
         */
        public DnsRecord(int rType, @NonNull String dName, int nsType, int nsClass, long ttl,
                @Nullable String rDataStr) throws IOException {
            this.rType = rType;
            this.dName = dName;
            this.nsType = nsType;
            this.nsClass = nsClass;
            if (rType != QDSECTION) {
                switch (nsType) {
                    case TYPE_A:
                    case TYPE_AAAA:
                        mRdata = InetAddresses.parseNumericAddress(rDataStr).getAddress();
                        break;
                    case TYPE_CNAME:
                        mRdata = stringToLabels(rDataStr);
                        break;
                    default:
                        throw new ParseException("Unsupported nsType: " + nsType);
                }
                this.ttl = ttl;
            } else {
                mRdata = null;
                this.ttl = 0;
            }
        }

        /**
         * Get a copy of rdata.
         */
        @Nullable
        public byte[] getRR() {
            return (mRdata == null) ? null : mRdata.clone();
        }

        /**
         * Convert label from {@code byte[]} to {@code String}
         *
         * Follows the same conversion rules of the native code (ns_name.c in libc)
         */
        private String labelToString(@NonNull byte[] label) {
            final StringBuffer sb = new StringBuffer();
            for (int i = 0; i < label.length; ++i) {
                int b = BitUtils.uint8(label[i]);
                // Control characters and non-ASCII characters.
                if (b <= 0x20 || b >= 0x7f) {
                    // Append the byte as an escaped decimal number, e.g., "\19" for 0x13.
                    sb.append('\\');
                    byteFormat.format(b, sb, pos);
                } else if (b == '"' || b == '.' || b == ';' || b == '\\'
                        || b == '(' || b == ')' || b == '@' || b == '$') {
                    // Append the byte as an escaped character, e.g., "\:" for 0x3a.
                    sb.append('\\');
                    sb.append((char) b);
                } else {
                    // Append the byte as a character, e.g., "a" for 0x61.
                    sb.append((char) b);
                }
            }
            return sb.toString();
        }

        /**
         * Simple implementation converts ascii string to labels according to RFC 1035.
         *
         * @param str ascii {@code String} that needs to be converted.
         * @return An encoded byte array that constructed by labels, and ends with zero-length
         * label.
         */
        private @NonNull byte[] stringToLabels(@NonNull String str) throws
                IOException, ParseException {
            if (TextUtils.isEmpty(str)) {
                throw new ParseException("Fail to parse empty string");
            }
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            String[] labels = str.split("\\.");
            for (String label : labels) {
                if (label.length() > MAXLABELSIZE) {
                    throw new ParseException("label is too long: " + label);
                }
                buf.write(label.length());
                buf.write(label.getBytes(StandardCharsets.US_ASCII));
            }
            buf.write(0x00); // end with zero-length label
            return buf.toByteArray();
        }

        private String parseName(@NonNull ByteBuffer buf, int depth) throws
                BufferUnderflowException, ParseException {
            if (depth > MAXLABELCOUNT) {
                throw new ParseException("Failed to parse name, too many labels");
            }
            final int len = BitUtils.uint8(buf.get());
            final int mask = len & NAME_COMPRESSION;
            if (0 == len) {
                return "";
            } else if (mask != NAME_NORMAL && mask != NAME_COMPRESSION) {
                throw new ParseException("Parse name fail, bad label type");
            } else if (mask == NAME_COMPRESSION) {
                // Name compression based on RFC 1035 - 4.1.4 Message compression
                final int offset = ((len & ~NAME_COMPRESSION) << 8) + BitUtils.uint8(buf.get());
                final int oldPos = buf.position();
                if (offset >= oldPos - 2) {
                    throw new ParseException("Parse compression name fail, invalid compression");
                }
                buf.position(offset);
                final String pointed = parseName(buf, depth + 1);
                buf.position(oldPos);
                return pointed;
            } else {
                final byte[] label = new byte[len];
                buf.get(label);
                final String head = labelToString(label);
                if (head.length() > MAXLABELSIZE) {
                    throw new ParseException("Parse name fail, invalid label length");
                }
                final String tail = parseName(buf, depth + 1);
                return TextUtils.isEmpty(tail) ? head : head + "." + tail;
            }
        }

        public @NonNull byte[] getBytes() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(baos);
            dos.write(stringToLabels(dName));
            dos.writeShort(nsType);
            dos.writeShort(nsClass);
            if (rType != QDSECTION) {
                dos.writeInt((int) ttl);
                if (mRdata == null) {
                    dos.writeShort(0);
                } else {
                    dos.writeShort(mRdata.length);
                    dos.write(mRdata);
                }
            }
            return baos.toByteArray();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;
            DnsRecord dnsRecord = (DnsRecord) o;
            return rType == dnsRecord.rType
                    && nsType == dnsRecord.nsType
                    && nsClass == dnsRecord.nsClass
                    && ttl == dnsRecord.ttl
                    && TextUtils.equals(dName, dnsRecord.dName)
                    && Arrays.equals(mRdata, dnsRecord.mRdata);
        }

        @Override
        public String toString() {
            return "DnsRecord{"
                    + "rType=" + rType
                    + ", dName='" + dName + '\''
                    + ", nsType=" + nsType
                    + ", nsClass=" + nsClass
                    + ", ttl=" + ttl
                    + ", mRdata=" + Arrays.toString(mRdata)
                    + '}';
        }
    }

    public static final int QDSECTION = 0;
    public static final int ANSECTION = 1;
    public static final int NSSECTION = 2;
    public static final int ARSECTION = 3;
    static final int NUM_SECTIONS = ARSECTION + 1;

    private static final String TAG = DnsPacket.class.getSimpleName();

    protected final DnsHeader mHeader;
    protected final List<DnsRecord>[] mRecords;

    protected DnsPacket(@NonNull byte[] data) throws ParseException {
        if (null == data) throw new ParseException("Parse header failed, null input data");
        final ByteBuffer buffer;
        try {
            buffer = ByteBuffer.wrap(data);
            mHeader = new DnsHeader(buffer);
        } catch (BufferUnderflowException e) {
            throw new ParseException("Parse Header fail, bad input data", e);
        }

        mRecords = new ArrayList[NUM_SECTIONS];

        for (int i = 0; i < NUM_SECTIONS; ++i) {
            final int count = mHeader.getRecordCount(i);
            if (count > 0) {
                mRecords[i] = new ArrayList(count);
            }
            for (int j = 0; j < count; ++j) {
                try {
                    mRecords[i].add(new DnsRecord(i, buffer));
                } catch (BufferUnderflowException e) {
                    throw new ParseException("Parse record fail", e);
                }
            }
        }
    }

    protected DnsPacket(@NonNull DnsHeader header, @Nullable ArrayList<DnsRecord> qd,
            @Nullable ArrayList<DnsRecord> ans) {
        mHeader = header;
        mRecords = new ArrayList[NUM_SECTIONS];
        mRecords[QDSECTION] = qd;
        mRecords[ANSECTION] = ans;
    }

    public @NonNull byte[] getBytes() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(mHeader.getBytes());

        for (int i = 0; i < NUM_SECTIONS; ++i) {
            final int count = mHeader.getRecordCount(i);
            if (count > 0) {
                for (int j = 0; j < count; ++j) {
                    buf.write(mRecords[i].get(j).getBytes());
                }
            }
        }
        return buf.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        DnsPacket dnsPacket = (DnsPacket) o;
        return Objects.equals(mHeader, dnsPacket.mHeader)
                && Arrays.deepEquals(mRecords, dnsPacket.mRecords);
    }
}
