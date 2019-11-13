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

package com.android.overlaytest;

import static android.content.Context.OVERLAY_SERVICE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.res.Resources;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
@MediumTest
public class TransactionTest {
    static final String APP_OVERLAY_ONE_PKG = "com.android.overlaytest.app_overlay_one";
    static final String APP_OVERLAY_TWO_PKG = "com.android.overlaytest.app_overlay_two";

    private Context mContext;
    private Resources mResources;
    private OverlayManager mOverlayManager;
    private int mUserId;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mResources = mContext.getResources();
        mOverlayManager = (OverlayManager) mContext.getSystemService(OVERLAY_SERVICE);
        mUserId = UserHandle.myUserId();

        Executor executor = (cmd) -> new Thread(cmd).start();
        LocalOverlayManager.setEnabledAndWait(executor, APP_OVERLAY_ONE_PKG, false);
        LocalOverlayManager.setEnabledAndWait(executor, APP_OVERLAY_TWO_PKG, false);
    }

    @Test
    public void testValidTransaction() throws Exception {
        assertOverlayIsEnabled(APP_OVERLAY_ONE_PKG, false, mUserId);
        assertOverlayIsEnabled(APP_OVERLAY_TWO_PKG, false, mUserId);

        OverlayManagerTransaction t = new OverlayManagerTransaction.Builder()
                .setEnabled(APP_OVERLAY_ONE_PKG, true, mUserId)
                .setEnabled(APP_OVERLAY_TWO_PKG, true, mUserId)
                .build();
        assertTrue(mOverlayManager.commit(t));

        assertOverlayIsEnabled(APP_OVERLAY_ONE_PKG, true, mUserId);
        assertOverlayIsEnabled(APP_OVERLAY_TWO_PKG, true, mUserId);
    }

    @Test
    public void testInvalidRequestHasNoEffect() {
        assertOverlayIsEnabled(APP_OVERLAY_ONE_PKG, false, mUserId);
        assertOverlayIsEnabled(APP_OVERLAY_TWO_PKG, false, mUserId);

        OverlayManagerTransaction t = new OverlayManagerTransaction.Builder()
                .setEnabled(APP_OVERLAY_ONE_PKG, true, mUserId)
                .setEnabled("does-not-exist", true, mUserId)
                .setEnabled(APP_OVERLAY_TWO_PKG, true, mUserId)
                .build();
        assertFalse(mOverlayManager.commit(t));

        assertOverlayIsEnabled(APP_OVERLAY_ONE_PKG, false, mUserId);
        assertOverlayIsEnabled(APP_OVERLAY_TWO_PKG, false, mUserId);
    }

    private void assertOverlayIsEnabled(final String packageName, boolean enabled, int userId) {
        final OverlayInfo oi = mOverlayManager.getOverlayInfo(packageName, UserHandle.of(userId));
        assertNotNull(oi);
        assertEquals(oi.isEnabled(), enabled);
    }
}
