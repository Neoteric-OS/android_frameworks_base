/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi.util;

import static org.junit.Assert.*;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link ScanResultUtil}.
 */
@SmallTest
public class ScanResultUtilTest {

    @Test
    public void testScanResultMatchingWithNetwork() {
        final String ssid = "Another SSid";
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = ScanResultUtil.createQuotedSSID(ssid);
        ScanResult scanResult = new ScanResult(ssid, "ab:cd:01:ef:45:89", 1245, 0, "",
                -78, 2450, 1025, 22, 33, 20, 0, 0, true);

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        scanResult.capabilities = "";
        assertTrue(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.wepKeys[0] = "45592364648547";
        scanResult.capabilities = "WEP";
        assertTrue(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        scanResult.capabilities = "PSK";
        assertTrue(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        scanResult.capabilities = "EAP";
        assertTrue(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));
    }

    @Test
    public void testNetworkCreationFromScanResult() {
        final String ssid = "Another SSid";
        ScanResult scanResult = new ScanResult(ssid, "ab:cd:01:ef:45:89", 1245, 0, "",
                -78, 2450, 1025, 22, 33, 20, 0, 0, true);
        WifiConfiguration config;

        scanResult.capabilities = "";
        config = ScanResultUtil.createNetworkFromScanResult(scanResult);
        assertEquals(config.SSID, ScanResultUtil.createQuotedSSID(ssid));
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));

        scanResult.capabilities = "WEP";
        config = ScanResultUtil.createNetworkFromScanResult(scanResult);
        assertEquals(config.SSID, ScanResultUtil.createQuotedSSID(ssid));
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertTrue(config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
        assertTrue(config.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.SHARED));

        scanResult.capabilities = "PSK";
        config = ScanResultUtil.createNetworkFromScanResult(scanResult);
        assertEquals(config.SSID, ScanResultUtil.createQuotedSSID(ssid));
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));

        scanResult.capabilities = "EAP";
        config = ScanResultUtil.createNetworkFromScanResult(scanResult);
        assertEquals(config.SSID, ScanResultUtil.createQuotedSSID(ssid));
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP));
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
    }
}
