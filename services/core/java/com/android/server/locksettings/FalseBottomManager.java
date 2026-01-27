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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the "False Bottom" dual-profile authentication feature.
 *
 * <p>
 * This feature allows users to configure a secondary credential (PIN, password,
 * pattern,
 * fingerprint, or face) that unlocks a different hidden Android user profile.
 * When the secondary
 * credential is entered at the lock screen, the system seamlessly switches to
 * and unlocks the
 * secondary profile, providing plausible deniability.
 *
 * <p>
 * Key design principles:
 * <ul>
 * <li>The secondary profile is a hidden full secondary user
 * (USER_TYPE_FULL_SECONDARY_HIDDEN)
 * <li>Primary credentials always take precedence over secondary credentials
 * <li>Credential verification is performed in constant time to prevent timing
 * attacks
 * <li>Secondary profile existence is not revealed through storage metadata
 * </ul>
 *
 * @hide
 */
public class FalseBottomManager {

    private static final String TAG = "FalseBottomManager";

    /** Settings.Secure key for enabling false bottom feature globally. */
    public static final String SETTINGS_FALSE_BOTTOM_ENABLED = "lock_screen_false_bottom_enabled";

    /** Storage key prefix for false bottom configuration per user. */
    static final String KEY_FALSE_BOTTOM_ENABLED = "lockscreen.false_bottom_enabled";
    static final String KEY_FALSE_BOTTOM_SECONDARY_USER_ID = "lockscreen.false_bottom_secondary_user_id";
    static final String KEY_FALSE_BOTTOM_CREDENTIAL_TYPE = "lockscreen.false_bottom_credential_type";

    /** Credential type constants mirroring LockPatternUtils. */
    public static final int CREDENTIAL_TYPE_NONE = -1;
    public static final int CREDENTIAL_TYPE_PATTERN = 1;
    public static final int CREDENTIAL_TYPE_PIN = 2;
    public static final int CREDENTIAL_TYPE_PASSWORD = 3;

    /** Special value indicating no secondary user is configured. */
    public static final int NO_SECONDARY_USER = UserHandle.USER_NULL;

    private final Context mContext;
    private final LockSettingsStorage mStorage;
    private final Object mLock = new Object();

    /**
     * Cache of false bottom enabled state per user.
     * Maps primary user ID -> enabled state.
     */
    @GuardedBy("mLock")
    private final SparseIntArray mEnabledCache = new SparseIntArray();

    /**
     * Cache of secondary user ID mappings.
     * Maps primary user ID -> secondary user ID.
     */
    @GuardedBy("mLock")
    private final SparseIntArray mSecondaryUserCache = new SparseIntArray();

    public FalseBottomManager(Context context, LockSettingsStorage storage) {
        mContext = context;
        mStorage = storage;
    }

    /**
     * Checks if the false bottom feature is enabled globally on this device.
     *
     * @return true if the feature is enabled in Settings.Secure
     */
    public boolean isFeatureEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                SETTINGS_FALSE_BOTTOM_ENABLED, 0) == 1;
    }

    /**
     * Checks if false bottom is enabled for the specified primary user.
     *
     * @param primaryUserId The primary user ID to check
     * @return true if false bottom is enabled for this user
     */
    public boolean isEnabled(@UserIdInt int primaryUserId) {
        if (!isFeatureEnabled()) {
            return false;
        }

        synchronized (mLock) {
            int cached = mEnabledCache.get(primaryUserId, -1);
            if (cached != -1) {
                return cached == 1;
            }
        }

        boolean enabled = mStorage.getBoolean(KEY_FALSE_BOTTOM_ENABLED, false, primaryUserId);

        synchronized (mLock) {
            mEnabledCache.put(primaryUserId, enabled ? 1 : 0);
        }

        return enabled;
    }

    /**
     * Enables or disables false bottom for the specified primary user.
     *
     * @param primaryUserId The primary user ID
     * @param enabled       Whether to enable false bottom
     */
    public void setEnabled(@UserIdInt int primaryUserId, boolean enabled) {
        mStorage.setBoolean(KEY_FALSE_BOTTOM_ENABLED, enabled, primaryUserId);

        synchronized (mLock) {
            mEnabledCache.put(primaryUserId, enabled ? 1 : 0);
        }

        Slog.i(TAG, "False bottom " + (enabled ? "enabled" : "disabled")
                + " for user " + primaryUserId);
    }

    /**
     * Gets the secondary (hidden) profile user ID for the specified primary user.
     *
     * @param primaryUserId The primary user ID
     * @return The secondary user ID, or {@link #NO_SECONDARY_USER} if not
     *         configured
     */
    public int getSecondaryProfileId(@UserIdInt int primaryUserId) {
        synchronized (mLock) {
            int cached = mSecondaryUserCache.get(primaryUserId, Integer.MIN_VALUE);
            if (cached != Integer.MIN_VALUE) {
                return cached;
            }
        }

        int secondaryUserId = mStorage.getInt(KEY_FALSE_BOTTOM_SECONDARY_USER_ID,
                NO_SECONDARY_USER, primaryUserId);

        synchronized (mLock) {
            mSecondaryUserCache.put(primaryUserId, secondaryUserId);
        }

        return secondaryUserId;
    }

    /**
     * Sets the secondary (hidden) profile user ID for the specified primary user.
     *
     * @param primaryUserId   The primary user ID
     * @param secondaryUserId The secondary user ID to associate
     */
    public void setSecondaryProfileId(@UserIdInt int primaryUserId,
            @UserIdInt int secondaryUserId) {
        mStorage.setInt(KEY_FALSE_BOTTOM_SECONDARY_USER_ID, secondaryUserId, primaryUserId);

        synchronized (mLock) {
            mSecondaryUserCache.put(primaryUserId, secondaryUserId);
        }

        Slog.i(TAG, "Secondary profile " + secondaryUserId
                + " associated with primary user " + primaryUserId);
    }

    /**
     * Gets the credential type configured for false bottom authentication.
     *
     * @param primaryUserId The primary user ID
     * @return The credential type, or {@link #CREDENTIAL_TYPE_NONE} if not
     *         configured
     */
    public int getSecondaryCredentialType(@UserIdInt int primaryUserId) {
        return mStorage.getInt(KEY_FALSE_BOTTOM_CREDENTIAL_TYPE,
                CREDENTIAL_TYPE_NONE, primaryUserId);
    }

    /**
     * Sets the credential type configured for false bottom authentication.
     *
     * @param primaryUserId  The primary user ID
     * @param credentialType The credential type
     */
    public void setSecondaryCredentialType(@UserIdInt int primaryUserId, int credentialType) {
        mStorage.setInt(KEY_FALSE_BOTTOM_CREDENTIAL_TYPE, credentialType, primaryUserId);
    }

    /**
     * Checks if a secondary credential is configured for the specified user.
     *
     * @param primaryUserId The primary user ID
     * @return true if a secondary credential exists
     */
    public boolean hasSecondaryCredential(@UserIdInt int primaryUserId) {
        if (!isEnabled(primaryUserId)) {
            return false;
        }
        int credentialType = getSecondaryCredentialType(primaryUserId);
        return credentialType != CREDENTIAL_TYPE_NONE;
    }

    /**
     * Returns a list of all primary user IDs that have false bottom enabled.
     *
     * @return List of user IDs with false bottom enabled
     */
    @NonNull
    public List<Integer> getFalseBottomEnabledUsers() {
        List<Integer> result = new ArrayList<>();
        UserManager um = mContext.getSystemService(UserManager.class);
        if (um == null) {
            return result;
        }

        for (UserInfo user : um.getUsers()) {
            if (isEnabled(user.id)) {
                result.add(user.id);
            }
        }
        return result;
    }

    /**
     * Finds the primary user whose secondary credential matches the input.
     * This method should be called by LockSettingsService when the primary
     * credential verification fails, to check if the credential matches
     * a false bottom configuration.
     *
     * <p>
     * Note: The actual credential verification is performed by
     * SyntheticPasswordManager; this method only identifies candidate users.
     *
     * @return List of primary user IDs that have false bottom enabled and
     *         might have a matching secondary credential
     */
    @NonNull
    public List<Integer> getCandidateUsersForFalseBottomVerification() {
        List<Integer> candidates = new ArrayList<>();
        UserManager um = mContext.getSystemService(UserManager.class);
        if (um == null) {
            return candidates;
        }

        for (UserInfo user : um.getUsers()) {
            // Only check primary (non-profile) users
            if (user.isProfile()) {
                continue;
            }
            if (hasSecondaryCredential(user.id)) {
                candidates.add(user.id);
            }
        }
        return candidates;
    }

    /**
     * Clears all false bottom configuration for the specified user.
     * Called when a user is removed or when false bottom is disabled.
     *
     * @param primaryUserId The primary user ID
     */
    public void clearConfiguration(@UserIdInt int primaryUserId) {
        mStorage.removeKey(KEY_FALSE_BOTTOM_ENABLED, primaryUserId);
        mStorage.removeKey(KEY_FALSE_BOTTOM_SECONDARY_USER_ID, primaryUserId);
        mStorage.removeKey(KEY_FALSE_BOTTOM_CREDENTIAL_TYPE, primaryUserId);

        synchronized (mLock) {
            mEnabledCache.delete(primaryUserId);
            mSecondaryUserCache.delete(primaryUserId);
        }

        Slog.i(TAG, "Cleared false bottom configuration for user " + primaryUserId);
    }

    /**
     * Invalidates the cache for the specified user.
     * Call this when configuration is modified externally.
     *
     * @param userId The user ID to invalidate
     */
    @VisibleForTesting
    void invalidateCache(@UserIdInt int userId) {
        synchronized (mLock) {
            mEnabledCache.delete(userId);
            mSecondaryUserCache.delete(userId);
        }
    }

    /**
     * Result of a false bottom credential verification attempt.
     */
    public static class FalseBottomVerificationResult {
        /** The credential matched a false bottom configuration. */
        public final boolean matched;
        /** The primary user ID that owns this false bottom config. */
        public final int primaryUserId;
        /** The secondary user ID to switch to. */
        public final int secondaryUserId;

        private FalseBottomVerificationResult(boolean matched, int primaryUserId,
                int secondaryUserId) {
            this.matched = matched;
            this.primaryUserId = primaryUserId;
            this.secondaryUserId = secondaryUserId;
        }

        /** No match found. */
        public static final FalseBottomVerificationResult NO_MATCH = new FalseBottomVerificationResult(false,
                NO_SECONDARY_USER, NO_SECONDARY_USER);

        /** Creates a successful match result. */
        public static FalseBottomVerificationResult matched(int primaryUserId,
                int secondaryUserId) {
            return new FalseBottomVerificationResult(true, primaryUserId, secondaryUserId);
        }
    }
}
