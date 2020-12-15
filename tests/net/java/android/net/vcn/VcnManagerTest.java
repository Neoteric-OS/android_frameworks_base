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

package android.net.vcn;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.vcn.VcnManager.VcnUnderlyingNetworkPolicyListener;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

public class VcnManagerTest {
    private static final Executor INLINE_EXECUTOR = Runnable::run;

    private IVcnManagementService mMockVcnManagementService;
    private VcnUnderlyingNetworkPolicyListener mMockPolicyListener;
    private VcnUnderlyingNetworkPolicy mMockPolicy;

    private Context mContext;
    private VcnManager mVcnManager;

    @Before
    public void setUp() {
        mMockVcnManagementService = mock(IVcnManagementService.class);
        mMockPolicyListener = mock(VcnUnderlyingNetworkPolicyListener.class);
        mMockPolicy = mock(VcnUnderlyingNetworkPolicy.class);

        mContext = getContext();
        mVcnManager = new VcnManager(mContext, mMockVcnManagementService);
    }

    @Test
    public void testRegisterVcnUnderlyingNetworkPolicyListener() throws Exception {
        mVcnManager.registerVcnUnderlyingNetworkPolicyListener(
                INLINE_EXECUTOR, mMockPolicyListener);

        ArgumentCaptor<IVcnUnderlyingNetworkPolicyListener> captor =
                ArgumentCaptor.forClass(IVcnUnderlyingNetworkPolicyListener.class);
        verify(mMockVcnManagementService)
                .registerVcnUnderlyingNetworkPolicyListener(captor.capture());

        IVcnUnderlyingNetworkPolicyListener listenerWrapper = captor.getValue();
        listenerWrapper.onPolicyChanged(mMockPolicy);

        verify(mMockPolicyListener).onPolicyChanged(eq(mMockPolicy));
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterVcnUnderlyingNetworkPolicyListenerNullExecutor() throws Exception {
        mVcnManager.registerVcnUnderlyingNetworkPolicyListener(null, mMockPolicyListener);
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterVcnUnderlyingNetworkPolicyListenerNullListener() throws Exception {
        mVcnManager.registerVcnUnderlyingNetworkPolicyListener(INLINE_EXECUTOR, null);
    }
}
