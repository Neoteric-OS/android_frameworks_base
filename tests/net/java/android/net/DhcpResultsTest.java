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

import static android.net.InetAddresses.parseNumericAddress;
import static android.net.ip.IpClientUtil.convertDhcpResultsToDhcpInfo;

import static org.junit.Assert.assertEquals;

import android.net.shared.Inet4AddressUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;

/**
 * Test for DhcpResults
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class DhcpResultsTest {
    private static final String STR_ADDR1 = "192.168.10.100";
    private static final String STR_ADDR2 = "127.0.0.1";
    private static final String STR_ADDR3 = "192.168.1.0";
    private static final String STR_ADDR4 = "192.168.1.1";
    private static final String SERVER_ADDR = "192.168.10.8";
    private static final int TEST_LEASE_TIME = 9999;
    private static final int TEST_MTU = 1450;
    private static final String TEST_DOMAINS = "example.com";
    private static final String TEST_VENDOR_INFO = "TEST_VENDOR_INFO";
    private static final String TEST_SRV_HOST_NAME = "dhcp.example.com";

    private DhcpResults createDhcpResults() {
        final DhcpResults results = new DhcpResults();
        results.ipAddress = new LinkAddress(parseNumericAddress(STR_ADDR1), 16);
        results.gateway = parseNumericAddress(STR_ADDR2);
        results.dnsServers.add(parseNumericAddress(STR_ADDR3));
        results.dnsServers.add(parseNumericAddress(STR_ADDR4));
        results.domains = TEST_DOMAINS;
        results.serverAddress = (Inet4Address) parseNumericAddress(SERVER_ADDR);
        results.vendorInfo = TEST_VENDOR_INFO;
        results.leaseDuration = TEST_LEASE_TIME;
        results.serverHostName = TEST_SRV_HOST_NAME;
        results.mtu = TEST_MTU;

        return results;
    }

    // Create DhcpInfo instance with the same data.
    private DhcpInfo createDhcpInfo() {
        final DhcpInfo dhcpInfo = new DhcpInfo();
        final LinkAddress linkAddr = new LinkAddress(parseNumericAddress(STR_ADDR1), 16);
        final int ipAddr = Inet4AddressUtils.inet4AddressToIntHTL(
                (Inet4Address) linkAddr.getAddress());
        dhcpInfo.setIpAddress(ipAddr);
        dhcpInfo.setGateway(ipToInteger(STR_ADDR2));
        dhcpInfo.setDns1(ipToInteger(STR_ADDR3));
        dhcpInfo.setDns2(ipToInteger(STR_ADDR4));
        dhcpInfo.setServerAddress(ipToInteger(SERVER_ADDR));
        dhcpInfo.setLeaseDuration(TEST_LEASE_TIME);
        dhcpInfo.setMtu(TEST_MTU);
        dhcpInfo.setDomains(TEST_DOMAINS);
        dhcpInfo.setVendorInfo(TEST_VENDOR_INFO);
        dhcpInfo.setServerHostName(TEST_SRV_HOST_NAME);

        return dhcpInfo;
    }

    private int ipToInteger(String ipString) {
        String[] ipSegs = ipString.split("[.]");
        int tmp = Integer.parseInt(ipSegs[3]) << 24 | Integer.parseInt(ipSegs[2]) << 16
                | Integer.parseInt(ipSegs[1]) << 8 | Integer.parseInt(ipSegs[0]);
        return tmp;
    }

    @Test
    public void testConvertDhcpResultsToDhcpInfo() {
        final DhcpResults dhcpResults = createDhcpResults();
        final DhcpInfo expected = createDhcpInfo();
        final DhcpInfo resultsToInfo = convertDhcpResultsToDhcpInfo(dhcpResults);
        assertEquals(expected, resultsToInfo);
    }
}
