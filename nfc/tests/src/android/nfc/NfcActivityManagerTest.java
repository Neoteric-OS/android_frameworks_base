/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.nfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Application;
import android.nfc.NfcActivityManager.NfcActivityState;
import android.nfc.NfcActivityManager.NfcApplicationState;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NfcActivityManagerTest {

    @Mock
    private NfcAdapter mMockNfcAdapter;
    private Application mMockApp;
    private Activity mMockActivity;
    private NfcActivityState mMockNfcActyState;
    private NfcApplicationState mMockAppState;
    private NfcActivityManager mNfcActivityManager;
    private MockitoSession mStaticMockSession;

    @Before
    public void setUp() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcAdapter.class)
                .strictness(Strictness.LENIENT).startMocking();
        MockitoAnnotations.initMocks(this);
        mMockApp = mock(Application.class);
        mMockActivity = mock(Activity.class);
        mMockNfcActyState = mock(NfcActivityState.class);
        mMockAppState = mock(NfcApplicationState.class);

        List<NfcApplicationState> mApps = new ArrayList<>(1);
        List<NfcActivityState> mActivities = new LinkedList<>();
        mActivities.add(mMockNfcActyState);
        mApps.add(mMockAppState);

        mNfcActivityManager = new NfcActivityManager(mMockNfcAdapter, mActivities, mApps);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testRegisterApplication_FirstTime_ShouldRegisterCallbacks() {
        mNfcActivityManager.registerApplication(mMockApp);

        NfcActivityManager.NfcApplicationState appState = mNfcActivityManager.findAppState(
                mMockApp);
        assertNotNull(appState);
        assertEquals(1, appState.refCount);
        verify(mMockApp).registerActivityLifecycleCallbacks(mNfcActivityManager);
    }
}
