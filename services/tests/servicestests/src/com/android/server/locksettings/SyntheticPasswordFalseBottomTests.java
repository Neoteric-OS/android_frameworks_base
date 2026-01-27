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

package com.android.server.locksettings;

import static android.content.pm.UserInfo.FLAG_PRIMARY;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.PropertyInvalidatedCache;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for False Bottom credential handling in SyntheticPasswordManager.
 *
 * atest FrameworksServicesTests:SyntheticPasswordFalseBottomTests
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class SyntheticPasswordFalseBottomTests extends BaseLockSettingsServiceTests {

    private static final int PRIMARY_USER = 0;
    private static final int FALSE_BOTTOM_USER = 99;

    @Before
    public void disableProcessCaches() {
        PropertyInvalidatedCache.disableForTestMode();
    }

    // ==================== Primary Credential Tests ====================

    @Test
    public void testSetAndVerifyPrimaryCredential() throws RemoteException {
        LockscreenCredential password = newPassword("primary-password");

        mService.initializeSyntheticPassword(PRIMARY_USER);
        assertTrue(mService.setLockCredential(password, nonePassword(), PRIMARY_USER));

        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(password, PRIMARY_USER, 0).getResponseCode());
    }

    @Test
    public void testPrimaryCredentialDoesNotUnlockFalseBottomUser() throws RemoteException {
        LockscreenCredential primaryPassword = newPassword("primary-password");
        LockscreenCredential falseBottomPassword = newPassword("false-bottom-password");

        // Initialize both users
        mService.initializeSyntheticPassword(PRIMARY_USER);
        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);

        // Set credentials for both
        assertTrue(mService.setLockCredential(primaryPassword, nonePassword(), PRIMARY_USER));
        assertTrue(mService.setLockCredential(falseBottomPassword, nonePassword(),
                FALSE_BOTTOM_USER));

        // Primary password should not unlock false bottom user
        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(primaryPassword, FALSE_BOTTOM_USER, 0)
                        .getResponseCode());
    }

    // ==================== False Bottom Credential Tests ====================

    @Test
    public void testSetAndVerifyFalseBottomCredential() throws RemoteException {
        LockscreenCredential falseBottomPassword = newPassword("false-bottom-password");

        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);
        assertTrue(mService.setLockCredential(falseBottomPassword, nonePassword(),
                FALSE_BOTTOM_USER));

        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(falseBottomPassword, FALSE_BOTTOM_USER, 0)
                        .getResponseCode());
    }

    @Test
    public void testFalseBottomCredentialDoesNotUnlockPrimaryUser() throws RemoteException {
        LockscreenCredential primaryPassword = newPassword("primary-password");
        LockscreenCredential falseBottomPassword = newPassword("false-bottom-password");

        // Initialize both users
        mService.initializeSyntheticPassword(PRIMARY_USER);
        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);

        // Set credentials for both
        assertTrue(mService.setLockCredential(primaryPassword, nonePassword(), PRIMARY_USER));
        assertTrue(mService.setLockCredential(falseBottomPassword, nonePassword(),
                FALSE_BOTTOM_USER));

        // False bottom password should not unlock primary user
        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(falseBottomPassword, PRIMARY_USER, 0)
                        .getResponseCode());
    }

    // ==================== Credential Change Tests ====================

    @Test
    public void testChangeFalseBottomCredential() throws RemoteException {
        LockscreenCredential password = newPassword("original-password");
        LockscreenCredential newPassword = newPassword("new-password");

        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);
        assertTrue(mService.setLockCredential(password, nonePassword(), FALSE_BOTTOM_USER));

        // Change password
        assertTrue(mService.setLockCredential(newPassword, password, FALSE_BOTTOM_USER));

        // Old password should fail
        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(password, FALSE_BOTTOM_USER, 0).getResponseCode());

        // New password should work
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(newPassword, FALSE_BOTTOM_USER, 0).getResponseCode());
    }

    @Test
    public void testClearFalseBottomCredential() throws RemoteException {
        LockscreenCredential password = newPassword("password");

        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);
        assertTrue(mService.setLockCredential(password, nonePassword(), FALSE_BOTTOM_USER));

        // Clear password
        assertTrue(mService.setLockCredential(nonePassword(), password, FALSE_BOTTOM_USER));

        // Verify no credential is set
        assertEquals(CREDENTIAL_TYPE_NONE, mService.getCredentialType(FALSE_BOTTOM_USER));
    }

    // ==================== PIN Tests ====================

    @Test
    public void testSetAndVerifyFalseBottomPIN() throws RemoteException {
        LockscreenCredential pin = newPin("123456");

        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);
        assertTrue(mService.setLockCredential(pin, nonePassword(), FALSE_BOTTOM_USER));

        assertEquals(CREDENTIAL_TYPE_PIN, mService.getCredentialType(FALSE_BOTTOM_USER));
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(pin, FALSE_BOTTOM_USER, 0).getResponseCode());
    }

    @Test
    public void testDifferentPINsForPrimaryAndFalseBottom() throws RemoteException {
        LockscreenCredential primaryPin = newPin("111111");
        LockscreenCredential falseBottomPin = newPin("999999");

        mService.initializeSyntheticPassword(PRIMARY_USER);
        mService.initializeSyntheticPassword(FALSE_BOTTOM_USER);

        assertTrue(mService.setLockCredential(primaryPin, nonePassword(), PRIMARY_USER));
        assertTrue(mService.setLockCredential(falseBottomPin, nonePassword(), FALSE_BOTTOM_USER));

        // Each PIN only works for its respective user
        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(primaryPin, PRIMARY_USER, 0).getResponseCode());
        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(primaryPin, FALSE_BOTTOM_USER, 0).getResponseCode());

        assertEquals(VerifyCredentialResponse.RESPONSE_OK,
                mService.verifyCredential(falseBottomPin, FALSE_BOTTOM_USER, 0).getResponseCode());
        assertEquals(VerifyCredentialResponse.RESPONSE_ERROR,
                mService.verifyCredential(falseBottomPin, PRIMARY_USER, 0).getResponseCode());
    }

    // ==================== Helper Constants ====================

    private static final int CREDENTIAL_TYPE_NONE =
            com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
}
