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
package android.tethering.mts;

import static android.net.cts.util.CtsTetheringUtils.isWifiTetheringSupported;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import android.app.UiAutomation;
import android.content.Context;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.TetheringManager;
import android.net.cts.util.CtsNetUtils;
import android.net.cts.util.CtsTetheringUtils;
import android.net.cts.util.CtsTetheringUtils.TestTetheringEventCallback;
import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TetheringModuleTest {
    private Context mContext;
    private TetheringManager mTm;
    private CtsNetUtils mCtsNetUtils;
    private CtsTetheringUtils mCtsTetheringUtils;

    private void adoptShellPermissionIdentity() {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
    }

    private void dropShellPermissionIdentity() {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Before
    public void setUp() throws Exception {
        adoptShellPermissionIdentity();
        mContext = InstrumentationRegistry.getContext();
        mTm = mContext.getSystemService(TetheringManager.class);
        mCtsNetUtils = new CtsNetUtils(mContext);
        mCtsTetheringUtils = new CtsTetheringUtils(mContext);
    }

    @After
    public void tearDown() throws Exception {
        dropShellPermissionIdentity();
    }

    private static final String TETHER_DISABLE_SELECT_ALL_PREFIX_RANGES =
            "tether_disable_select_all_prefix_ranges";
    @Test
    public void testSwitchPrefixRangeWhenConflict() throws Exception {
        assumeFalse(isFeatureDisabled(TETHER_DISABLE_SELECT_ALL_PREFIX_RANGES, false));

        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();
        tetherEventCallback.assumeTetheringSupported();

        if (!isWifiTetheringSupported(tetherEventCallback)) {
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
            return;
        }

        TestNetworkInterface tni = null;
        try {
            mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            final List<String> tetheredIfaces = tetherEventCallback.getTetheredInterfaces();
            assertEquals(1, tetheredIfaces.size());
            final String wifiTetheringIface = tetheredIfaces.get(0);

            NetworkInterface nif = NetworkInterface.getByName(wifiTetheringIface);
            // Tethering downstream only have one ipv4 address.
            final LinkAddress hotspotAddr = getFirstIpv4Address(nif);
            assertNotNull(hotspotAddr);

            final IpPrefix testPrefix = getConflictAddress(hotspotAddr);
            assertNotNull(testPrefix);

            tni = setUpTestNetwork(
                    new LinkAddress(testPrefix.getAddress(), testPrefix.getPrefixLength()));

            mCtsTetheringUtils.expectSoftApDisabled();
            tetherEventCallback.expectTetheredInterfacesChanged(null);
            final List<String> wifiRegexs =
                    tetherEventCallback.getTetheringInterfaceRegexps().getTetherableWifiRegexs();

            tetherEventCallback.expectTetheredInterfacesChanged(wifiRegexs);
            nif = NetworkInterface.getByName(wifiTetheringIface);
            final LinkAddress newHotspotAddr = getFirstIpv4Address(nif);
            assertNotNull(newHotspotAddr);
            final IpPrefix newTestPrefix = getConflictAddress(newHotspotAddr);
            assertNotEquals(testPrefix, newTestPrefix);

            mCtsTetheringUtils.stopWifiTethering(tetherEventCallback);
        } finally {
            if (tni != null) {
                tni.getFileDescriptor().close();
            }
            mTm.stopAllTethering();
        }
    }

    private LinkAddress getFirstIpv4Address(final NetworkInterface nif) {
        for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
            final LinkAddress addr = new LinkAddress(ia.getAddress(), ia.getNetworkPrefixLength());
            if (addr.isIpv4()) return addr;
        }
        return null;
    }

    private IpPrefix getConflictAddress(final LinkAddress address) {
        final ArrayList<IpPrefix> prefixPool = new ArrayList<>(Arrays.asList(
                new IpPrefix("192.168.0.0/16"),
                new IpPrefix("172.16.0.0/12"),
                new IpPrefix("10.0.0.0/8")));

        for (IpPrefix prefix : prefixPool) {
            if (prefix.contains(address.getAddress())) return prefix;
        }

        return null;
    }

    private TestNetworkInterface setUpTestNetwork(final LinkAddress address) throws Exception {
        TestNetworkManager tnm = mContext.getSystemService(TestNetworkManager.class);
        final TestNetworkInterface tni = tnm.createTunInterface(new LinkAddress[] {address});
        mCtsNetUtils.setupAndGetTestNetwork(tni.getInterfaceName()).waitForAvailable();

        return tni;
    }

    public static boolean isFeatureDisabled(final String name, final boolean defaultValue) {
        return DeviceConfig.getBoolean(NAMESPACE_CONNECTIVITY, name, defaultValue);
    }
}
