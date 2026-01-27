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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.provider.Settings;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for the False Bottom dual-profile authentication feature.
 *
 * atest FrameworksServicesTests:FalseBottomServiceTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FalseBottomServiceTests extends BaseLockSettingsServiceTests {

    private static final int HIDDEN_SECONDARY_USER_ID = 99;

    @Before
    public void setUp() throws Exception {
        super.setUp_baseServices();

        // Enable false bottom feature globally
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomManager.SETTINGS_FALSE_BOTTOM_ENABLED, 1);
    }

    // ==================== Feature Enable/Disable Tests ====================

    @Test
    public void testIsFalseBottomFeatureEnabled_whenDisabled_returnsFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomManager.SETTINGS_FALSE_BOTTOM_ENABLED, 0);

        assertFalse(mService.isFalseBottomFeatureEnabled());
    }

    @Test
    public void testIsFalseBottomFeatureEnabled_whenEnabled_returnsTrue() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                FalseBottomManager.SETTINGS_FALSE_BOTTOM_ENABLED, 1);

        assertTrue(mService.isFalseBottomFeatureEnabled());
    }

    // ==================== Per-User Enable/Disable Tests ====================

    @Test
    public void testIsFalseBottomEnabled_defaultsToFalse() {
        assertFalse(mService.isFalseBottomEnabled(PRIMARY_USER_ID));
    }

    @Test
    public void testSetFalseBottomEnabled_persistsState() {
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);

        assertTrue(mService.isFalseBottomEnabled(PRIMARY_USER_ID));
    }

    @Test
    public void testSetFalseBottomEnabled_canBeDisabled() {
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, false);

        assertFalse(mService.isFalseBottomEnabled(PRIMARY_USER_ID));
    }

    // ==================== Secondary User Configuration Tests ====================

    @Test
    public void testGetFalseBottomSecondaryUserId_defaultsToNoSecondaryUser() {
        int secondaryUserId = mService.getFalseBottomSecondaryUserId(PRIMARY_USER_ID);

        assertEquals(FalseBottomManager.NO_SECONDARY_USER, secondaryUserId);
    }

    @Test
    public void testSetFalseBottomSecondaryUserId_persistsMapping() {
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, HIDDEN_SECONDARY_USER_ID);

        int retrieved = mService.getFalseBottomSecondaryUserId(PRIMARY_USER_ID);
        assertEquals(HIDDEN_SECONDARY_USER_ID, retrieved);
    }

    // ==================== Credential Configuration Tests ====================

    @Test
    public void testHasFalseBottomSecondaryCredential_defaultsToFalse() {
        assertFalse(mService.hasFalseBottomSecondaryCredential(PRIMARY_USER_ID));
    }

    @Test
    public void testSetFalseBottomCredential_failsWithoutSecondaryUser() {
        // Don't configure secondary user
        LockscreenCredential credential = newPin("5678");

        boolean result = mService.setFalseBottomCredential(credential, PRIMARY_USER_ID);

        assertFalse(result);
        credential.zeroize();
    }

    @Test
    public void testSetFalseBottomCredential_succeedsWithSecondaryUser() throws Exception {
        // Configure the secondary user
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, SECONDARY_USER_ID);

        LockscreenCredential secondaryCredential = newPin("5678");
        boolean result = mService.setFalseBottomCredential(secondaryCredential, PRIMARY_USER_ID);

        assertTrue(result);
        assertTrue(mService.hasFalseBottomSecondaryCredential(PRIMARY_USER_ID));
        secondaryCredential.zeroize();
    }

    // ==================== Credential Verification Tests ====================

    @Test
    public void testVerifyCredential_primaryTakesPrecedence() throws Exception {
        // Set primary credential
        LockscreenCredential primaryCredential = newPin("1234");
        mService.setLockCredential(primaryCredential, nonePassword(), PRIMARY_USER_ID);

        // Configure false bottom with secondary credential
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, SECONDARY_USER_ID);
        LockscreenCredential secondaryCredential = newPin("5678");
        mService.setFalseBottomCredential(secondaryCredential, PRIMARY_USER_ID);

        // Verify primary credential still unlocks primary user
        VerifyCredentialResponse response = mService.verifyCredential(
                primaryCredential, PRIMARY_USER_ID, 0);

        assertEquals(VerifyCredentialResponse.RESPONSE_OK, response.getResponseCode());

        primaryCredential.zeroize();
        secondaryCredential.zeroize();
    }

    @Test
    public void testClearFalseBottomCredential_clearsConfiguration() {
        // Set up false bottom
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, SECONDARY_USER_ID);

        // Clear it
        mService.clearFalseBottomCredential(PRIMARY_USER_ID);

        assertFalse(mService.hasFalseBottomSecondaryCredential(PRIMARY_USER_ID));
    }

    // ==================== FalseBottomManager Unit Tests ====================

    @Test
    public void testFalseBottomManager_getCandidateUsers_returnsOnlyConfiguredUsers() {
        // Create manager directly for unit testing
        FalseBottomManager manager = new FalseBottomManager(mContext, mStorage);

        // Configure one user
        manager.setEnabled(PRIMARY_USER_ID, true);
        manager.setSecondaryCredentialType(PRIMARY_USER_ID,
                FalseBottomManager.CREDENTIAL_TYPE_PIN);

        java.util.List<Integer> candidates = manager.getCandidateUsersForFalseBottomVerification();

        // Should include PRIMARY_USER_ID since it's configured
        assertTrue(candidates.contains(PRIMARY_USER_ID));
    }

    @Test
    public void testFalseBottomManager_clearConfiguration_removesAllSettings() {
        FalseBottomManager manager = new FalseBottomManager(mContext, mStorage);

        // Configure
        manager.setEnabled(PRIMARY_USER_ID, true);
        manager.setSecondaryProfileId(PRIMARY_USER_ID, HIDDEN_SECONDARY_USER_ID);
        manager.setSecondaryCredentialType(PRIMARY_USER_ID,
                FalseBottomManager.CREDENTIAL_TYPE_PASSWORD);

        // Clear
        manager.clearConfiguration(PRIMARY_USER_ID);

        // Verify all cleared
        assertFalse(manager.isEnabled(PRIMARY_USER_ID));
        assertEquals(FalseBottomManager.NO_SECONDARY_USER,
                manager.getSecondaryProfileId(PRIMARY_USER_ID));
        assertEquals(FalseBottomManager.CREDENTIAL_TYPE_NONE,
                manager.getSecondaryCredentialType(PRIMARY_USER_ID));
    }

    @Test
    public void testFalseBottomVerificationResult_noMatch() {
        FalseBottomManager.FalseBottomVerificationResult result =
                FalseBottomManager.FalseBottomVerificationResult.NO_MATCH;

        assertFalse(result.matched);
        assertEquals(FalseBottomManager.NO_SECONDARY_USER, result.primaryUserId);
        assertEquals(FalseBottomManager.NO_SECONDARY_USER, result.secondaryUserId);
    }

    @Test
    public void testFalseBottomVerificationResult_matched() {
        FalseBottomManager.FalseBottomVerificationResult result =
                FalseBottomManager.FalseBottomVerificationResult.matched(
                        PRIMARY_USER_ID, HIDDEN_SECONDARY_USER_ID);

        assertTrue(result.matched);
        assertEquals(PRIMARY_USER_ID, result.primaryUserId);
        assertEquals(HIDDEN_SECONDARY_USER_ID, result.secondaryUserId);
    }

    // ==================== Cache Invalidation Tests ====================

    @Test
    public void testFalseBottomManager_cacheInvalidation_clearsEnabledCache() {
        // Use test constructor that doesn't register observer
        FalseBottomManager manager = new FalseBottomManager(mContext, mStorage, false);

        // Set enabled and verify cached
        manager.setEnabled(PRIMARY_USER_ID, true);
        assertTrue(manager.isEnabled(PRIMARY_USER_ID));

        // Manually invalidate cache (simulating Settings change)
        manager.invalidateCache(PRIMARY_USER_ID);

        // Should still return same value (reads from storage)
        assertTrue(manager.isEnabled(PRIMARY_USER_ID));

        // Now disable in storage directly and invalidate
        mStorage.setBoolean(FalseBottomManager.KEY_FALSE_BOTTOM_ENABLED, false, PRIMARY_USER_ID);
        manager.invalidateCache(PRIMARY_USER_ID);

        // Should now return false
        assertFalse(manager.isEnabled(PRIMARY_USER_ID));
    }

    @Test
    public void testFalseBottomManager_cacheInvalidation_clearsSecondaryUserCache() {
        FalseBottomManager manager = new FalseBottomManager(mContext, mStorage, false);

        // Set secondary and verify cached
        manager.setSecondaryProfileId(PRIMARY_USER_ID, HIDDEN_SECONDARY_USER_ID);
        assertEquals(HIDDEN_SECONDARY_USER_ID, manager.getSecondaryProfileId(PRIMARY_USER_ID));

        // Invalidate and check still reads from storage
        manager.invalidateCache(PRIMARY_USER_ID);
        assertEquals(HIDDEN_SECONDARY_USER_ID, manager.getSecondaryProfileId(PRIMARY_USER_ID));
    }

    // ==================== Credential Cleanup Tests ====================

    @Test
    public void testClearFalseBottomCredential_clearsConfiguration() {
        // Set up false bottom
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, SECONDARY_USER_ID);

        LockscreenCredential credential = newPin("5678");
        mService.setFalseBottomCredential(credential, PRIMARY_USER_ID);
        credential.zeroize();

        assertTrue(mService.hasFalseBottomSecondaryCredential(PRIMARY_USER_ID));

        // Clear it
        mService.clearFalseBottomCredential(PRIMARY_USER_ID);

        // Verify configuration is cleared
        assertFalse(mService.hasFalseBottomSecondaryCredential(PRIMARY_USER_ID));
        assertFalse(mService.isFalseBottomEnabled(PRIMARY_USER_ID));
    }

    @Test
    public void testClearFalseBottomCredential_clearsSecondaryUserAssociation() {
        // Set up false bottom with secondary user
        mService.setFalseBottomEnabled(PRIMARY_USER_ID, true);
        mService.setFalseBottomSecondaryUserId(PRIMARY_USER_ID, HIDDEN_SECONDARY_USER_ID);

        // Clear it
        mService.clearFalseBottomCredential(PRIMARY_USER_ID);

        // Verify secondary user association is cleared
        assertEquals(FalseBottomManager.NO_SECONDARY_USER,
                mService.getFalseBottomSecondaryUserId(PRIMARY_USER_ID));
    }
}

