/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NetworkStackIpMemoryStore;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.ipmemorystore.NetworkAttributes;
import android.net.shared.InitialConfiguration;
import android.net.shared.ProvisioningConfiguration;
import android.net.util.InterfaceParams;
import android.os.ConditionVariable;
import android.os.IBinder;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.NetworkObserver;
import com.android.server.NetworkObserverRegistry;
import com.android.server.NetworkStackService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for IpClient.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpClientIntegrationTest {
    private static final int DEFAULT_AVOIDBADWIFI_CONFIG_VALUE = 1;

    private static final String VALID = "VALID";
    private static final String INVALID = "INVALID";
    private static final String TEST_IFNAME = "test_wlan0";
    private static final int TEST_IFINDEX = 1001;
    // See RFC 7042#section-2.1.2 for EUI-48 documentation values.
    private static final MacAddress TEST_MAC = MacAddress.fromString("00:00:5E:00:53:01");
    private static final int TEST_TIMEOUT_MS = 400;
    private static final String TEST_L2KEY = "some l2key";
    private static final String TEST_GROUPHINT = "some grouphint";

    @Mock private Context mContext;
    @Mock private ConnectivityManager mCm;
    @Mock private NetworkObserverRegistry mObserverRegistry;
    @Mock private INetd mNetd;
    @Mock private Resources mResources;
    @Mock private IIpClientCallbacks mCb;
    @Mock private AlarmManager mAlarm;
    @Mock private IpClient.Dependencies mDependencies;
    @Mock private ContentResolver mContentResolver;
    @Mock private NetworkStackService.NetworkStackServiceManager mNetworkStackServiceManager;
    @Mock private NetworkStackIpMemoryStore mIpMemoryStore;

    private NetworkObserver mObserver;
    private InterfaceParams mIfParams;
    private TestNetworkInterface mIface;
    private String mIfaceName;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(eq(Context.ALARM_SERVICE))).thenReturn(mAlarm);
        when(mContext.getSystemService(eq(ConnectivityManager.class))).thenReturn(mCm);
        when(mContext.getResources()).thenReturn(mResources);
        when(mDependencies.getNetd(any())).thenReturn(mNetd);
        when(mResources.getInteger(R.integer.config_networkAvoidBadWifi))
                .thenReturn(DEFAULT_AVOIDBADWIFI_CONFIG_VALUE);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);

        mIfParams = null;
    }

    @Test
    public void testIpClientCallbacks() throws Exception {
        /* TODO: make this implement all IIpClientCallbacks or don't use it.
        class TestIpClientCallback implements IIpClientCallbacks {
            IIpClient mIIpClient;
            ConditionVariable mCreatedCv;

            @Override
            public int getInterfaceVersion() {
                return this.VERSION;
            }

            @Override
            public void onIpClientCreated(IIpClient iIpClient) {
                mIIpClient = iIpClient;
                mCreatedCv.open();
            }

            public IIpClient getIIpClient() {
                mCreatedCv.block();
                return mIIpClient;
            }
        }
        */

        // Adopt the shell permission identity to create a test TAP interface.
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        TestNetworkManager tnm = (TestNetworkManager) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.TEST_NETWORK_SERVICE);
        mIface = tnm.createTapInterface();
        mIfaceName = mIface.getInterfaceName();
        // Drop the identity in order to regain the network stack permissions, which the shell does
        // not have.
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();

        IBinder netdIBinder = (IBinder) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.NETD_SERVICE);
        INetd netd = INetd.Stub.asInterface(netdIBinder);
        when(mContext.getSystemService(eq(Context.NETD_SERVICE))).thenReturn(netdIBinder);
        assertNotNull(netd);

        NetworkObserverRegistry reg = new NetworkObserverRegistry();
        reg.register(netd);
        IpClient ipc = new IpClient(mContext, mIfaceName, mCb, reg, mNetworkStackServiceManager);

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder().build();
        // ipc.startProvisioning(config);
    }
}
