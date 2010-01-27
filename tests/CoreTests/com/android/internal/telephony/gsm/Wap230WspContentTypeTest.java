/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.util.HexDump;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class Wap230WspContentTypeTest extends TestCase {

    public static final Map<Integer, String> WELL_KNOWN_MIME_TYPES = new HashMap<Integer, String>();
    public static final Map<Integer, String> WELL_KNOWN_PARAMETERS = new HashMap<Integer, String>();

    static {
        WELL_KNOWN_MIME_TYPES.put(0x00, "*/*");
        WELL_KNOWN_MIME_TYPES.put(0x01, "text/*");
        WELL_KNOWN_MIME_TYPES.put(0x02, "text/html");
        WELL_KNOWN_MIME_TYPES.put(0x03, "text/plain");
        WELL_KNOWN_MIME_TYPES.put(0x04, "text/x-hdml");
        WELL_KNOWN_MIME_TYPES.put(0x05, "text/x-ttml");
        WELL_KNOWN_MIME_TYPES.put(0x06, "text/x-vCalendar");
        WELL_KNOWN_MIME_TYPES.put(0x07, "text/x-vCard");
        WELL_KNOWN_MIME_TYPES.put(0x08, "text/vnd.wap.wml");
        WELL_KNOWN_MIME_TYPES.put(0x09, "text/vnd.wap.wmlscript");
        WELL_KNOWN_MIME_TYPES.put(0x0A, "text/vnd.wap.wta-event");
        WELL_KNOWN_MIME_TYPES.put(0x0B, "multipart/*");
        WELL_KNOWN_MIME_TYPES.put(0x0C, "multipart/mixed");
        WELL_KNOWN_MIME_TYPES.put(0x0D, "multipart/form-data");
        WELL_KNOWN_MIME_TYPES.put(0x0E, "multipart/byterantes");
        WELL_KNOWN_MIME_TYPES.put(0x0F, "multipart/alternative");
        WELL_KNOWN_MIME_TYPES.put(0x10, "application/*");
        WELL_KNOWN_MIME_TYPES.put(0x11, "application/java-vm");
        WELL_KNOWN_MIME_TYPES.put(0x12, "application/x-www-form-urlencoded");
        WELL_KNOWN_MIME_TYPES.put(0x13, "application/x-hdmlc");
        WELL_KNOWN_MIME_TYPES.put(0x14, "application/vnd.wap.wmlc");
        WELL_KNOWN_MIME_TYPES.put(0x15, "application/vnd.wap.wmlscriptc");
        WELL_KNOWN_MIME_TYPES.put(0x16, "application/vnd.wap.wta-eventc");
        WELL_KNOWN_MIME_TYPES.put(0x17, "application/vnd.wap.uaprof");
        WELL_KNOWN_MIME_TYPES.put(0x18, "application/vnd.wap.wtls-ca-certificate");
        WELL_KNOWN_MIME_TYPES.put(0x19, "application/vnd.wap.wtls-user-certificate");
        WELL_KNOWN_MIME_TYPES.put(0x1A, "application/x-x509-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(0x1B, "application/x-x509-user-cert");
        WELL_KNOWN_MIME_TYPES.put(0x1C, "image/*");
        WELL_KNOWN_MIME_TYPES.put(0x1D, "image/gif");
        WELL_KNOWN_MIME_TYPES.put(0x1E, "image/jpeg");
        WELL_KNOWN_MIME_TYPES.put(0x1F, "image/tiff");
        WELL_KNOWN_MIME_TYPES.put(0x20, "image/png");
        WELL_KNOWN_MIME_TYPES.put(0x21, "image/vnd.wap.wbmp");
        WELL_KNOWN_MIME_TYPES.put(0x22, "application/vnd.wap.multipart.*");
        WELL_KNOWN_MIME_TYPES.put(0x23, "application/vnd.wap.multipart.mixed");
        WELL_KNOWN_MIME_TYPES.put(0x24, "application/vnd.wap.multipart.form-data");
        WELL_KNOWN_MIME_TYPES.put(0x25, "application/vnd.wap.multipart.byteranges");
        WELL_KNOWN_MIME_TYPES.put(0x26, "application/vnd.wap.multipart.alternative");
        WELL_KNOWN_MIME_TYPES.put(0x27, "application/xml");
        WELL_KNOWN_MIME_TYPES.put(0x28, "text/xml");
        WELL_KNOWN_MIME_TYPES.put(0x29, "application/vnd.wap.wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x2A, "application/x-x968-cross-cert");
        WELL_KNOWN_MIME_TYPES.put(0x2B, "application/x-x968-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(0x2C, "application/x-x968-user-cert");
        WELL_KNOWN_MIME_TYPES.put(0x2D, "text/vnd.wap.si");
        WELL_KNOWN_MIME_TYPES.put(0x2E, "application/vnd.wap.sic");
        WELL_KNOWN_MIME_TYPES.put(0x2F, "text/vnd.wap.sl");
        WELL_KNOWN_MIME_TYPES.put(0x30, "application/vnd.wap.slc");
        WELL_KNOWN_MIME_TYPES.put(0x31, "text/vnd.wap.co");
        WELL_KNOWN_MIME_TYPES.put(0x32, "application/vnd.wap.coc");
        WELL_KNOWN_MIME_TYPES.put(0x33, "application/vnd.wap.multipart.related");
        WELL_KNOWN_MIME_TYPES.put(0x34, "application/vnd.wap.sia");
        WELL_KNOWN_MIME_TYPES.put(0x35, "text/vnd.wap.connectivity-xml");
        WELL_KNOWN_MIME_TYPES.put(0x36, "application/vnd.wap.connectivity-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x37, "application/pkcs7-mime");
        WELL_KNOWN_MIME_TYPES.put(0x38, "application/vnd.wap.hashed-certificate");
        WELL_KNOWN_MIME_TYPES.put(0x39, "application/vnd.wap.signed-certificate");
        WELL_KNOWN_MIME_TYPES.put(0x3A, "application/vnd.wap.cert-response");
        WELL_KNOWN_MIME_TYPES.put(0x3B, "application/xhtml+xml");
        WELL_KNOWN_MIME_TYPES.put(0x3C, "application/wml+xml");
        WELL_KNOWN_MIME_TYPES.put(0x3D, "text/css");
        WELL_KNOWN_MIME_TYPES.put(0x3E, "application/vnd.wap.mms-message");
        WELL_KNOWN_MIME_TYPES.put(0x3F, "application/vnd.wap.rollover-certificate");
        WELL_KNOWN_MIME_TYPES.put(0x40, "application/vnd.wap.locc+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x41, "application/vnd.wap.loc+xml");
        WELL_KNOWN_MIME_TYPES.put(0x42, "application/vnd.syncml.dm+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x43, "application/vnd.syncml.dm+xml");
        WELL_KNOWN_MIME_TYPES.put(0x44, "application/vnd.syncml.notification");
        WELL_KNOWN_MIME_TYPES.put(0x45, "application/vnd.wap.xhtml+xml");
        WELL_KNOWN_MIME_TYPES.put(0x46, "application/vnd.wv.csp.cir");
        WELL_KNOWN_MIME_TYPES.put(0x47, "application/vnd.oma.dd+xml");
        WELL_KNOWN_MIME_TYPES.put(0x48, "application/vnd.oma.drm.message");
        WELL_KNOWN_MIME_TYPES.put(0x49, "application/vnd.oma.drm.content");
        WELL_KNOWN_MIME_TYPES.put(0x4A, "application/vnd.oma.drm.rights+xml");
        WELL_KNOWN_MIME_TYPES.put(0x4B, "application/vnd.oma.drm.rights+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x4C, "application/vnd.wv.csp+xml");
        WELL_KNOWN_MIME_TYPES.put(0x4D, "application/vnd.wv.csp+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x4E, "application/vnd.syncml.ds.notification");
        WELL_KNOWN_MIME_TYPES.put(0x4F, "audio/*");
        WELL_KNOWN_MIME_TYPES.put(0x50, "video/*");
        WELL_KNOWN_MIME_TYPES.put(0x51, "application/vnd.oma.dd2+xml");
        WELL_KNOWN_MIME_TYPES.put(0x52, "application/mikey");
        WELL_KNOWN_MIME_TYPES.put(0x53, "application/vnd.oma.dcd");
        WELL_KNOWN_MIME_TYPES.put(0x54, "application/vnd.oma.dcdc");

        WELL_KNOWN_MIME_TYPES.put(0x0201, "application/vnd.uplanet.cacheop-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0202, "application/vnd.uplanet.signal");
        WELL_KNOWN_MIME_TYPES.put(0x0203, "application/vnd.uplanet.alert-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0204, "application/vnd.uplanet.list-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0205, "application/vnd.uplanet.listcmd-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0206, "application/vnd.uplanet.channel-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0207, "application/vnd.uplanet.provisioning-status-uri");
        WELL_KNOWN_MIME_TYPES.put(0x0208, "x-wap.multipart/vnd.uplanet.header-set");
        WELL_KNOWN_MIME_TYPES.put(0x0209, "application/vnd.uplanet.bearer-choice-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x020A, "application/vnd.phonecom.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x020B, "application/vnd.nokia.syncset+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x020C, "image/x-up-wpng");
        WELL_KNOWN_MIME_TYPES.put(0x0300, "application/iota.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0301, "application/iota.mmc-xml");
        WELL_KNOWN_MIME_TYPES.put(0x0302, "application/vnd.syncml+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0303, "application/vnd.syncml+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0304, "text/vnd.wap.emn+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0305, "text/calendar");
        WELL_KNOWN_MIME_TYPES.put(0x0306, "application/vnd.omads-email+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0307, "application/vnd.omads-file+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0308, "application/vnd.omads-folder+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0309, "text/directory;profile=vCard");
        WELL_KNOWN_MIME_TYPES.put(0x030A, "application/vnd.wap.emn+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x030B, "application/vnd.nokia.ipdc-purchase-response");
        WELL_KNOWN_MIME_TYPES.put(0x030C, "application/vnd.motorola.screen3+xml");
        WELL_KNOWN_MIME_TYPES.put(0x030D, "application/vnd.motorola.screen3+gzip");
        WELL_KNOWN_MIME_TYPES.put(0x030E, "application/vnd.cmcc.setting+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x030F, "application/vnd.cmcc.bombing+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0310, "application/vnd.docomo.pf");
        WELL_KNOWN_MIME_TYPES.put(0x0311, "application/vnd.docomo.ub");
        WELL_KNOWN_MIME_TYPES.put(0x0312, "application/vnd.omaloc-supl-init");
        WELL_KNOWN_MIME_TYPES.put(0x0313, "application/vnd.oma.group-usage-list+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0314, "application/oma-directory+xml");
        WELL_KNOWN_MIME_TYPES.put(0x0315, "application/vnd.docomo.pf2");
        WELL_KNOWN_MIME_TYPES.put(0x0316, "application/vnd.oma.drm.roap-trigger+wbxml");
        WELL_KNOWN_MIME_TYPES.put(0x0317, "application/vnd.sbm.mid2");
        WELL_KNOWN_MIME_TYPES.put(0x0318, "application/vnd.wmf.bootstrap");
        WELL_KNOWN_MIME_TYPES.put(0x0319, "application/vnc.cmcc.dcd+xml");
        WELL_KNOWN_MIME_TYPES.put(0x031A, "application/vnd.sbm.cid");
        WELL_KNOWN_MIME_TYPES.put(0x031B, "application/vnd.oma.bcast.provisioningtrigger");

        WELL_KNOWN_PARAMETERS.put(0x00, "Q");
        WELL_KNOWN_PARAMETERS.put(0x01, "Charset");
        WELL_KNOWN_PARAMETERS.put(0x02, "Level");
        WELL_KNOWN_PARAMETERS.put(0x03, "Type");
        WELL_KNOWN_PARAMETERS.put(0x07, "Differences");
        WELL_KNOWN_PARAMETERS.put(0x08, "Padding");
        WELL_KNOWN_PARAMETERS.put(0x09, "Type");
        WELL_KNOWN_PARAMETERS.put(0x0E, "Max-Age");
        WELL_KNOWN_PARAMETERS.put(0x10, "Secure");
        WELL_KNOWN_PARAMETERS.put(0x11, "SEC");
        WELL_KNOWN_PARAMETERS.put(0x12, "MAC");
        WELL_KNOWN_PARAMETERS.put(0x13, "Creation-date");
        WELL_KNOWN_PARAMETERS.put(0x14, "Modification-date");
        WELL_KNOWN_PARAMETERS.put(0x15, "Read-date");
        WELL_KNOWN_PARAMETERS.put(0x16, "Size");
        WELL_KNOWN_PARAMETERS.put(0x17, "Name");
        WELL_KNOWN_PARAMETERS.put(0x18, "Filename");
        WELL_KNOWN_PARAMETERS.put(0x19, "Start");
        WELL_KNOWN_PARAMETERS.put(0x1A, "Start-info");
        WELL_KNOWN_PARAMETERS.put(0x1B, "Comment");
        WELL_KNOWN_PARAMETERS.put(0x1C, "Domain");
        WELL_KNOWN_PARAMETERS.put(0x1D, "Path");

    }

    public void testWellKnownShortIntegerMimeTypeValues() {

        int numberFound = 0;

        for (int value : Wap230WspContentTypeTest.WELL_KNOWN_MIME_TYPES.keySet()) {
            if (value < 128) {
                WspTypeDecoder unit =
                    new WspTypeDecoder(HexDump.toByteArray((byte) (value | 0x80)));
                assertTrue(unit.decodeContentType(0));
                String mimeType = unit.getValueString();
                int wellKnownValue = (int) unit.getValue32();
                assertEquals(Wap230WspContentTypeTest.WELL_KNOWN_MIME_TYPES.get(value), mimeType);
                assertEquals(value, wellKnownValue);
                assertEquals(1, unit.getDecodedDataLength());
                numberFound++;
            }
        }

        assertEquals(0x55, numberFound);
    }

    public void testWellKnownLongIntegerMimeTypeValues() {

        int numberFound = 0;

        for (int value : Wap230WspContentTypeTest.WELL_KNOWN_MIME_TYPES.keySet()) {
            if (value > 0x0200) {
                byte[] stuff = new byte[10];
                stuff[0] = 3; // Value Length Short Length
                stuff[1] = 2; // Long Integer Short Length
                stuff[2] = (byte) (value >> 8);
                stuff[3] = (byte) (value & 0xFF);
                WspTypeDecoder unit = new WspTypeDecoder(stuff);
                assertTrue(unit.decodeContentType(0));
                String mimeType = unit.getValueString();
                int wellKnownValue = (int) unit.getValue32();
                assertEquals(Wap230WspContentTypeTest.WELL_KNOWN_MIME_TYPES.get(value), mimeType);
                assertEquals(value, wellKnownValue);
                assertEquals(4, unit.getDecodedDataLength());
                numberFound++;
            }
        }

        assertEquals(0x28, numberFound);
    }

    public void testConstrainedMediaExtensionMedia() throws Exception {

        String testType = "application/wibble";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(testType.getBytes("US-ASCII"));
        out.write(0x00);
        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));
        String mimeType = unit.getValueString();
        assertEquals("application/wibble", mimeType);
        assertEquals(-1, unit.getValue32());
        assertEquals(19, unit.getDecodedDataLength());
    }

    public void testGeneralFormShortLengthExtensionMedia() throws Exception {

        String testType = "12345678901234567890123456789";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(30);
        out.write(testType.getBytes("US-ASCII"));
        out.write(0x00);

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();
        assertEquals("12345678901234567890123456789", mimeType);
        assertEquals(-1, unit.getValue32());
        assertEquals(31, unit.getDecodedDataLength());
    }

    public void testGeneralFormShortLengthWellKnownShortInteger() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x01); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();
        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(2, unit.getDecodedDataLength());

    }

    public void testGeneralFormShortLengthWellKnownLongInteger() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(0x03); // Value-length, short-length
        out.write(0x02); // Well-known-media, long-integer (2 octets)
        out.write(0x03); // long int byte 1
        out.write(0x14); // long int byte 2

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/oma-directory+xml", mimeType);
        assertEquals(0x0314, unit.getValue32());
        assertEquals(4, unit.getDecodedDataLength());

    }

    public void testGeneralFormLengthQuoteWellKnownShortInteger() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(31); // Value-length, length-quote
        out.write(0x01); // Length as UINTVAR
        out.write(0x3F | 0x80); // Well-known-media, short-integer

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();
        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(3, unit.getDecodedDataLength());

    }

    public void testGeneralFormLengthQuoteWellKnownLongInteger() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(31); // Value-length, length-quote
        out.write(0x03); // Length as UINTVAR
        out.write(0x02); // Well-known-media, long-integer (2 octets)
        out.write(0x03); // long int byte 1
        out.write(0x14); // long int byte 2

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/oma-directory+xml", mimeType);
        assertEquals(0x0314, unit.getValue32());
        assertEquals(5, unit.getDecodedDataLength());

    }

    public void testGeneralFormLengthQuoteExtensionMedia() throws Exception {

        String testType = "application/wibble";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(31); // Value-length, length-quote
        out.write(testType.length() + 1); // Length as UINTVAR

        out.write(testType.getBytes("US-ASCII"));
        out.write(0x00);

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/wibble", mimeType);
        assertEquals(-1, unit.getValue32());
        assertEquals(21, unit.getDecodedDataLength());

    }

    public void testGeneralFormLengthQuoteExtensionMediaWithNiceLongMimeType() throws Exception {

        String testType =
                "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789012345678901234567890123456789";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(31); // Value-length, length-quote
        out.write(0x81); // Length as UINTVAR (161 decimal, 0xA1), 2 bytes
        out.write(0x21);

        out.write(testType.getBytes("US-ASCII"));
        out.write(0x00);

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals(
                "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                + "01234567890123456789012345678901234567890123456789012345678901234567890123456789",
                mimeType);
        assertEquals(-1, unit.getValue32());
        assertEquals(164, unit.getDecodedDataLength());

    }

    public void testConstrainedMediaExtensionMediaWithSpace() throws Exception {

        String testType = " application/wibble";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(testType.getBytes("US-ASCII"));
        out.write(0x00);

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals(" application/wibble", mimeType);
        assertEquals(-1, unit.getValue32());
        assertEquals(20, unit.getDecodedDataLength());

    }

    public void testTypedParamWellKnownShortIntegerNoValue() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x03); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x1C | 0x80); // Well-known-parameter token ('Domain'),
        // Short-integer
        out.write(0x00); // No value

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());

        assertEquals(4, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals(null, params.get("Domain"));

    }

    public void testTypedParamWellKnownShortIntegerTokenText() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x14); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x1C | 0x80); // Well-known-parameter token ('Domain'),
        // Short-integer
        out.write("wdstechnology.com".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());

        assertEquals(21, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("wdstechnology.com", params.get("Domain"));

    }

    public void testTypedParamWellKnownLongIntegerTokenText() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x15); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x01); // Short-length of well-known parameter token
        out.write(0x1C); // Well-known-parameter token ('Domain'),
        // Long-integer
        out.write("wdstechnology.com".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());

        assertEquals(22, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("wdstechnology.com", params.get("Domain"));

    }

    public void testTypedParamWellKnownShortIntegerQuotedText() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x15); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x1C | 0x80); // Well-known-parameter token ('Domain'),
        // Short-integer
        out.write(34); // Quote
        out.write("wdstechnology.com".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(22, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("wdstechnology.com", params.get("Domain"));

    }

    public void testTypedParamWellKnownShortIntegerCompactIntegerValue() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x3); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x11 | 0x80); // Well-known-parameter token ('SEC'),
        // Short-integer
        out.write(0x01 | 0x80); // Short-integer value

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(4, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("1", params.get("SEC"));

    }

    public void testTypedParamWellKnownShortIntegerMultipleParameters() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0B); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x11 | 0x80); // Well-known-parameter token ('SEC'),
        // Short-integer
        out.write(0x01 | 0x80); // Short-integer value

        out.write(0x12 | 0x80); // Well-known-parameter token ('MAC'), short
                                // integer
        out.write(34);
        out.write("imapc".getBytes("US-ASCII"));
        out.write(0x00);

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(12, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("1", params.get("SEC"));
        assertEquals("imapc", params.get("MAC"));
    }

    public void testUntypedParamIntegerValueShortInteger() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write("MYPARAM".getBytes("US-ASCII")); // Untyped Param
        out.write(0x00); // EOS
        out.write(0x45 | 0x80); // Short Integer value

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(11, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("69", params.get("MYPARAM"));
    }

    public void testUntypedParamIntegerValueLongInteger() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0C); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write("MYPARAM".getBytes("US-ASCII")); // Untyped Param
        out.write(0x00); // EOS
        out.write(0x02); // Short Length
        out.write(0x42); // Long Integer byte 1
        out.write(0x69); // Long Integer byte 2

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(13, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("17001", params.get("MYPARAM"));
    }

    public void testUntypedParamTextNoValue() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write("MYPARAM".getBytes("US-ASCII")); // Untyped Param
        out.write(0x00); // EOS
        out.write(0x00); // No value

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));
        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(11, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals(null, params.get("MYPARAM"));

    }

    public void testUntypedParamTextTokenText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x11); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write("MYPARAM".getBytes("US-ASCII")); // Untyped Param
        out.write(0x00); // EOS
        out.write("myvalue".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));
        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(18, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("myvalue", params.get("MYPARAM"));
    }

    public void testUntypedParamTextQuotedString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x11); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write("MYPARAM".getBytes("US-ASCII")); // Untyped Param
        out.write(0x00); // EOS
        out.write(34);
        out.write("myvalue".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));
        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(19, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("myvalue", params.get("MYPARAM"));

    }

    public void testTypedParamTextQValue() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x04); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x00); // Typed Param ('Q')
        out.write(0x83); // Q value byte 1
        out.write(0x31); // Q value byte 2

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));
        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());
        assertEquals(5, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("433", params.get("Q"));

    }

    public void testTypedParamUnassignedWellKnownShortIntegerTokenText() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x14); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x42 | 0x80); // Unassigned Well-known-parameter token,
        // Short-integer
        out.write("wdstechnology.com".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());

        assertEquals(21, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("wdstechnology.com", params.get("unassigned/0x42"));

    }

    public void testTypedParamUnassignedWellKnownLongIntegerTokenText() throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x15); // Value-length, short-length
        out.write(0x3F | 0x80); // Well-known-media, short-integer
        out.write(0x01); // Short-length of well-known parameter token
        out.write(0x42); // Well-known-parameter token ('Domain'),
        // Long-integer
        out.write("wdstechnology.com".getBytes("US-ASCII")); // Token text
        out.write(0x00); // EOS

        WspTypeDecoder unit = new WspTypeDecoder(out.toByteArray());
        assertTrue(unit.decodeContentType(0));

        String mimeType = unit.getValueString();

        assertEquals("application/vnd.wap.rollover-certificate", mimeType);
        assertEquals(0x3F, unit.getValue32());

        assertEquals(22, unit.getDecodedDataLength());

        Map<String, String> params = unit.getContentParameters();
        assertEquals("wdstechnology.com", params.get("unassigned/0x42"));

    }

}
