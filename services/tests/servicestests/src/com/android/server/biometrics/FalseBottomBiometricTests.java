/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.biometrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for FalseBottomBiometricHelper.
 *
 * atest FrameworksServicesTests:FalseBottomBiometricTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FalseBottomBiometricTests {

    private static final int PRIMARY_USER_ID = 0;
    private static final int SECONDARY_USER_ID = 10;
    private static final int HIDDEN_PROFILE_ID = 99;

    private Context mContext;
    private FalseBottomBiometricHelper mHelper;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHelper = new FalseBottomBiometricHelper(mContext);
    }

    // ==================== Feature Enable/Disable Tests ====================

    @Test
    public void testIsFeatureEnabled_whenDisabled_returnsFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 0);

        assertFalse(mHelper.isFeatureEnabled());
    }

    @Test
    public void testIsFeatureEnabled_whenEnabled_returnsTrue() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 1);

        assertTrue(mHelper.isFeatureEnabled());
    }

    // ==================== Biometric Enable/Disable Tests ====================

    @Test
    public void testIsBiometricFalseBottomEnabled_whenFeatureDisabled_returnsFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 0);

        assertFalse(mHelper.isBiometricFalseBottomEnabled(PRIMARY_USER_ID));
    }

    @Test
    public void testIsBiometricFalseBottomEnabled_whenBiometricNotEnabled_returnsFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 1);
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_BIOMETRIC_ENABLED,
                0, PRIMARY_USER_ID);

        assertFalse(mHelper.isBiometricFalseBottomEnabled(PRIMARY_USER_ID));
    }

    // ==================== Biometric Route Result Tests ====================

    @Test
    public void testBiometricRouteResult_noRoute() {
        FalseBottomBiometricHelper.BiometricRouteResult result =
                FalseBottomBiometricHelper.BiometricRouteResult.NO_ROUTE;

        assertFalse(result.shouldRoute);
        assertEquals(UserHandle.USER_NULL, result.targetUserId);
    }

    @Test
    public void testBiometricRouteResult_routeTo() {
        FalseBottomBiometricHelper.BiometricRouteResult result =
                FalseBottomBiometricHelper.BiometricRouteResult.routeTo(HIDDEN_PROFILE_ID);

        assertTrue(result.shouldRoute);
        assertEquals(HIDDEN_PROFILE_ID, result.targetUserId);
    }

    // ==================== Check Biometric Route Tests ====================

    @Test
    public void testCheckBiometricRoute_whenFeatureDisabled_returnsNoRoute() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 0);

        FalseBottomBiometricHelper.BiometricRouteResult result =
                mHelper.checkBiometricRoute(PRIMARY_USER_ID, PRIMARY_USER_ID);

        assertFalse(result.shouldRoute);
    }

    @Test
    public void testCheckBiometricRoute_sameUser_noRouting() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 1);

        FalseBottomBiometricHelper.BiometricRouteResult result =
                mHelper.checkBiometricRoute(PRIMARY_USER_ID, PRIMARY_USER_ID);

        // Same user should not trigger routing
        assertFalse(result.shouldRoute);
    }

    // ==================== Secondary User ID Tests ====================

    @Test
    public void testGetSecondaryBiometricUserId_whenNotConfigured_returnsNull() {
        int secondaryUserId = mHelper.getSecondaryBiometricUserId(PRIMARY_USER_ID);

        assertEquals(UserHandle.USER_NULL, secondaryUserId);
    }

    // ==================== Helper Instance Tests ====================

    @Test
    public void testHelperCreation_notNull() {
        assertNotNull(mHelper);
    }

    // ==================== UserSwitchCallback Tests ====================

    @Test
    public void testUserSwitchCallback_onComplete_callsCallback() {
        // Verify the callback interface can be instantiated and used
        final int[] capturedUserId = new int[1];
        final boolean[] callbackCalled = new boolean[1];

        FalseBottomBiometricHelper.UserSwitchCallback callback =
                new FalseBottomBiometricHelper.UserSwitchCallback() {
                    @Override
                    public void onUserSwitchComplete(int targetUserId) {
                        capturedUserId[0] = targetUserId;
                        callbackCalled[0] = true;
                    }

                    @Override
                    public void onUserSwitchFailed(int targetUserId, boolean timedOut) {
                        // Not testing failure path here
                    }
                };

        // Simulate a successful callback
        callback.onUserSwitchComplete(HIDDEN_PROFILE_ID);

        assertTrue(callbackCalled[0]);
        assertEquals(HIDDEN_PROFILE_ID, capturedUserId[0]);
    }

    @Test
    public void testUserSwitchCallback_onFailed_callsCallback() {
        final int[] capturedUserId = new int[1];
        final boolean[] timedOutFlag = new boolean[1];
        final boolean[] callbackCalled = new boolean[1];

        FalseBottomBiometricHelper.UserSwitchCallback callback =
                new FalseBottomBiometricHelper.UserSwitchCallback() {
                    @Override
                    public void onUserSwitchComplete(int targetUserId) {
                        // Not testing success path here
                    }

                    @Override
                    public void onUserSwitchFailed(int targetUserId, boolean timedOut) {
                        capturedUserId[0] = targetUserId;
                        timedOutFlag[0] = timedOut;
                        callbackCalled[0] = true;
                    }
                };

        // Simulate a timeout failure
        callback.onUserSwitchFailed(HIDDEN_PROFILE_ID, true);

        assertTrue(callbackCalled[0]);
        assertEquals(HIDDEN_PROFILE_ID, capturedUserId[0]);
        assertTrue(timedOutFlag[0]);

        // Simulate immediate failure
        callbackCalled[0] = false;
        callback.onUserSwitchFailed(SECONDARY_USER_ID, false);

        assertTrue(callbackCalled[0]);
        assertEquals(SECONDARY_USER_ID, capturedUserId[0]);
        assertFalse(timedOutFlag[0]);
    }

    // ==================== handleBiometricUnlockAsync Tests ====================

    @Test
    public void testHandleBiometricUnlockAsync_whenFeatureDisabled_returnsFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomBiometricHelper.SETTINGS_FALSE_BOTTOM_ENABLED, 0);

        FalseBottomBiometricHelper.UserSwitchCallback callback =
                new FalseBottomBiometricHelper.UserSwitchCallback() {
                    @Override
                    public void onUserSwitchComplete(int targetUserId) {}
                    @Override
                    public void onUserSwitchFailed(int targetUserId, boolean timedOut) {}
                };

        boolean result = mHelper.handleBiometricUnlockAsync(
                PRIMARY_USER_ID, PRIMARY_USER_ID, callback);

        assertFalse(result);
    }
}

