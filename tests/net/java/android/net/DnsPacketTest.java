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

import static android.net.DnsResolver.CLASS_IN;
import static android.net.DnsResolver.TYPE_AAAA;
import static android.net.DnsResolver.TYPE_CNAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DnsPacketTest {
    private void assertHeaderParses(DnsPacket.DnsHeader header, int id, int flag,
            int qCount, int aCount, int nsCount, int arCount) {
        assertEquals(header.id, id);
        assertEquals(header.flags, flag);
        assertEquals(header.getRecordCount(DnsPacket.QDSECTION), qCount);
        assertEquals(header.getRecordCount(DnsPacket.ANSECTION), aCount);
        assertEquals(header.getRecordCount(DnsPacket.NSSECTION), nsCount);
        assertEquals(header.getRecordCount(DnsPacket.ARSECTION), arCount);
    }

    private void assertRecordParses(DnsPacket.DnsRecord record, String dname,
            int dtype, int dclass, int ttl, byte[] rr) {
        assertEquals(record.dName, dname);
        assertEquals(record.nsType, dtype);
        assertEquals(record.nsClass, dclass);
        assertEquals(record.ttl, ttl);
        assertTrue(Arrays.equals(record.getRR(), rr));
    }

    class TestDnsPacket extends DnsPacket {
        TestDnsPacket(byte[] data) throws ParseException {
            super(data);
        }

        TestDnsPacket(@NonNull DnsHeader header, @Nullable ArrayList<DnsRecord> qd,
                @Nullable ArrayList<DnsRecord> ans) {
            super(header, qd, ans);
        }

        public DnsHeader getHeader() {
            return mHeader;
        }
        public List<DnsRecord> getRecordList(int secType) {
            return mRecords[secType];
        }
    }

    class TestDnsHeader extends DnsPacket.DnsHeader {
        TestDnsHeader(ByteBuffer buf) throws ParseException {
            super(buf);
        }

        TestDnsHeader(int id, int flags, int qcount, int anscount) {
            super(id, flags, qcount, anscount);
        }
    }

    class TestDnsRecord extends DnsPacket.DnsRecord {
        TestDnsRecord(int rType, ByteBuffer buf)
                throws BufferUnderflowException, ParseException {
            super(rType, buf);
        }

        TestDnsRecord(int rType, String dName, int nsType, int nsClass, long ttl,
                String rDataStr) throws IOException {
            super(rType, dName, nsType, nsClass, ttl, rDataStr);
        }
    }

    @Test
    public void testNullDisallowed() {
        try {
            new TestDnsPacket(null);
            fail("Exception not thrown for null byte array");
        } catch (ParseException e) {
        }
    }

    @Test
    public void testV4Answer() throws Exception {
        final byte[] v4blob = new byte[] {
            /* Header */
            0x55, 0x66, /* Transaction ID */
            (byte) 0x81, (byte) 0x80, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x01, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01, /* Class */
            /* Answers */
            (byte) 0xc0, 0x0c, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01, /* Class */
            0x00, 0x00, 0x01, 0x2b, /* TTL */
            0x00, 0x04, /* Data length */
            (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 /* Address */
        };
        TestDnsPacket packet = new TestDnsPacket(v4blob);

        // Header part
        assertHeaderParses(packet.getHeader(), 0x5566, 0x8180, 1, 1, 0, 0);

        // Record part
        List<DnsPacket.DnsRecord> qdRecordList =
                packet.getRecordList(DnsPacket.QDSECTION);
        assertEquals(qdRecordList.size(), 1);
        assertRecordParses(qdRecordList.get(0), "www.google.com", 1, 1, 0, null);

        List<DnsPacket.DnsRecord> anRecordList =
                packet.getRecordList(DnsPacket.ANSECTION);
        assertEquals(anRecordList.size(), 1);
        assertRecordParses(anRecordList.get(0), "www.google.com", 1, 1, 0x12b,
                new byte[]{ (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 });
    }

    @Test
    public void testV6Answer() throws Exception {
        final byte[] v6blob = new byte[] {
            /* Header */
            0x77, 0x22, /* Transaction ID */
            (byte) 0x81, (byte) 0x80, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x01, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x1c, /* Type */
            0x00, 0x01, /* Class */
            /* Answers */
            (byte) 0xc0, 0x0c, /* Name */
            0x00, 0x1c, /* Type */
            0x00, 0x01, /* Class */
            0x00, 0x00, 0x00, 0x37, /* TTL */
            0x00, 0x10, /* Data length */
            0x24, 0x04, 0x68, 0x00, 0x40, 0x05, 0x08, 0x0d,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x04 /* Address */
        };
        TestDnsPacket packet = new TestDnsPacket(v6blob);

        // Header part
        assertHeaderParses(packet.getHeader(), 0x7722, 0x8180, 1, 1, 0, 0);

        // Record part
        List<DnsPacket.DnsRecord> qdRecordList =
                packet.getRecordList(DnsPacket.QDSECTION);
        assertEquals(qdRecordList.size(), 1);
        assertRecordParses(qdRecordList.get(0), "www.google.com", 28, 1, 0, null);

        List<DnsPacket.DnsRecord> anRecordList =
                packet.getRecordList(DnsPacket.ANSECTION);
        assertEquals(anRecordList.size(), 1);
        assertRecordParses(anRecordList.get(0), "www.google.com", 28, 1, 0x37,
                new byte[]{ 0x24, 0x04, 0x68, 0x00, 0x40, 0x05, 0x08, 0x0d,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x04 });
    }

    /** Verifies that the synthesized {@link DnsPacket.DnsHeader} can be parsed correctly. */
    @Test
    public void testDnsHeaderSynthesize() {
        final TestDnsHeader testHeader = new TestDnsHeader(0x7722 /* id */,
                0x8180 /* flags */, 3 /* qcount */, 5 /* anscount */);
        final TestDnsHeader actualHeader = new TestDnsHeader(
                ByteBuffer.wrap(testHeader.getBytes()));
        assertEquals(testHeader, actualHeader);
    }

    /** Verifies that the synthesized {@link DnsPacket.DnsRecord} can be parsed correctly. */
    @Test
    public void testDnsRecordSynthesize() throws IOException {
        assertDnsRecordRoundTrip(new TestDnsRecord(DnsPacket.ANSECTION /* rType */,
                "test.com", TYPE_AAAA, CLASS_IN, 5 /* ttl */, "abcd::fedc"));
        assertDnsRecordRoundTrip(new TestDnsRecord(DnsPacket.QDSECTION, "test.com",
                TYPE_AAAA, CLASS_IN, 0 /* unused */, null));
    }

    private void assertDnsRecordRoundTrip(TestDnsRecord before)
            throws IOException {
        final TestDnsRecord after = new TestDnsRecord(before.rType,
                ByteBuffer.wrap(before.getBytes()));
        assertEquals(after, before);
    }

    /** Verifies that the synthesized {@link DnsPacket} can be parsed correctly. */
    @Test
    public void testDnsPacketSynthesize() throws IOException {
        final TestDnsHeader testHeader = new TestDnsHeader(0x7722 /* id */,
                0x8180 /* flags */, 1 /* qcount */, 3 /* anscount */);
        final ArrayList<DnsPacket.DnsRecord> qlist = new ArrayList<>();
        final ArrayList<DnsPacket.DnsRecord> alist = new ArrayList<>();
        qlist.add(new TestDnsRecord(DnsPacket.QDSECTION, "test.com", TYPE_AAAA, CLASS_IN, 0,
                null));
        alist.add(new TestDnsRecord(DnsPacket.ANSECTION, "test.com", TYPE_AAAA, CLASS_IN,
                7, "1234::5678"));
        alist.add(new TestDnsRecord(DnsPacket.ANSECTION, "test.com", TYPE_CNAME, CLASS_IN,
                9, "www.test.com"));
        alist.add(new TestDnsRecord(DnsPacket.ANSECTION, "www.test.com", TYPE_AAAA, CLASS_IN,
                11, "abcd::fedc"));

        assertEquals(testHeader, new TestDnsHeader(ByteBuffer.wrap(testHeader.getBytes())));

        final TestDnsPacket testPacket = new TestDnsPacket(testHeader, qlist, alist);
        final TestDnsPacket actualPacket = new TestDnsPacket(testPacket.getBytes());
        assertEquals(testPacket, actualPacket);
    }
}
