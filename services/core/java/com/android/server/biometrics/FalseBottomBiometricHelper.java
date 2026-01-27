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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.IActivityManager;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.widget.LockPatternUtils;

/**
 * Helper class to manage false bottom biometric authentication.
 *
 * <p>This class provides methods to determine if a biometric authentication should
 * route to a hidden secondary user profile (false bottom) instead of the current user.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Check if false bottom is enabled for biometric unlock
 *   <li>Determine if an authenticated biometric belongs to a false bottom user
 *   <li>Initiate user switch when false bottom biometric is detected
 * </ul>
 *
 * @hide
 */
public class FalseBottomBiometricHelper {

    private static final String TAG = "FalseBottomBiometricHelper";

    /** Settings.Secure key for enabling false bottom feature globally. */
    public static final String SETTINGS_FALSE_BOTTOM_ENABLED =
            "lock_screen_false_bottom_enabled";

    /** Settings.Secure key for false bottom biometric routing per user. */
    public static final String SETTINGS_FALSE_BOTTOM_BIOMETRIC_ENABLED =
            "lock_screen_false_bottom_biometric_enabled";

    private final Context mContext;
    private final LockPatternUtils mLockPatternUtils;

    public FalseBottomBiometricHelper(@NonNull Context context) {
        mContext = context;
        mLockPatternUtils = new LockPatternUtils(context);
    }

    /**
     * Checks if the false bottom feature is enabled globally.
     *
     * @return true if false bottom is enabled
     */
    public boolean isFeatureEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                SETTINGS_FALSE_BOTTOM_ENABLED, 0) == 1;
    }

    /**
     * Checks if biometric-based false bottom unlock is enabled for the given user.
     *
     * @param userId The user ID to check
     * @return true if biometric false bottom is enabled for this user
     */
    public boolean isBiometricFalseBottomEnabled(@UserIdInt int userId) {
        if (!isFeatureEnabled()) {
            return false;
        }
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                SETTINGS_FALSE_BOTTOM_BIOMETRIC_ENABLED, 0, userId) == 1;
    }

    /**
     * Gets the secondary (hidden) user ID associated with the primary user
     * for false bottom biometric unlock.
     *
     * @param primaryUserId The primary user ID
     * @return The secondary user ID, or UserHandle.USER_NULL if not configured
     */
    public int getSecondaryBiometricUserId(@UserIdInt int primaryUserId) {
        try {
            // Query LockSettingsService for the false bottom secondary user
            return mLockPatternUtils.getInt(
                    "lockscreen.false_bottom_secondary_user_id",
                    UserHandle.USER_NULL,
                    primaryUserId);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to get secondary biometric user ID", e);
            return UserHandle.USER_NULL;
        }
    }

    /**
     * Result of a false bottom biometric check.
     */
    public static class BiometricRouteResult {
        /** Whether this biometric should route to a false bottom user. */
        public final boolean shouldRoute;
        /** The target user ID to switch to. */
        public final int targetUserId;

        private BiometricRouteResult(boolean shouldRoute, int targetUserId) {
            this.shouldRoute = shouldRoute;
            this.targetUserId = targetUserId;
        }

        /** No routing needed - authenticate as normal user. */
        public static final BiometricRouteResult NO_ROUTE =
                new BiometricRouteResult(false, UserHandle.USER_NULL);

        /** Route to the specified false bottom user. */
        public static BiometricRouteResult routeTo(int targetUserId) {
            return new BiometricRouteResult(true, targetUserId);
        }
    }

    /**
     * Checks if the authenticated biometric should route to a false bottom user.
     *
     * <p>This method is called after biometric authentication succeeds to determine
     * if the authentication belongs to a hidden secondary user profile.
     *
     * @param authenticatedUserId The user ID for whom the biometric was enrolled
     * @param currentUserId The currently active foreground user
     * @return BiometricRouteResult indicating whether to route and target user
     */
    @NonNull
    public BiometricRouteResult checkBiometricRoute(
            @UserIdInt int authenticatedUserId,
            @UserIdInt int currentUserId) {

        if (!isFeatureEnabled()) {
            return BiometricRouteResult.NO_ROUTE;
        }

        // If the authenticated user IS the current user, no routing needed
        if (authenticatedUserId == currentUserId) {
            // But check if this is actually a false bottom biometric
            // enrolled for a different user
            int secondaryUserId = getSecondaryBiometricUserId(currentUserId);
            if (secondaryUserId != UserHandle.USER_NULL) {
                // Check if biometric false bottom is enabled
                if (isBiometricFalseBottomEnabled(currentUserId)) {
                    Slog.d(TAG, "Biometric authenticated for current user with FB enabled");
                }
            }
            return BiometricRouteResult.NO_ROUTE;
        }

        // Authenticated user is different from current user
        // This might be a false bottom biometric scenario
        if (isBiometricFalseBottomEnabled(currentUserId)) {
            int expectedSecondary = getSecondaryBiometricUserId(currentUserId);
            if (authenticatedUserId == expectedSecondary) {
                Slog.i(TAG, "False bottom biometric detected, routing to user "
                        + authenticatedUserId);
                return BiometricRouteResult.routeTo(authenticatedUserId);
            }
        }

        return BiometricRouteResult.NO_ROUTE;
    }

    /**
     * Initiates a user switch to the false bottom user.
     *
     * @param targetUserId The user ID to switch to
     * @return true if the switch was initiated successfully
     */
    public boolean switchToFalseBottomUser(@UserIdInt int targetUserId) {
        try {
            IActivityManager am = IActivityManager.Stub.asInterface(
                    ServiceManager.getService("activity"));
            if (am != null) {
                Slog.i(TAG, "Initiating user switch to false bottom user: " + targetUserId);
                return am.switchUser(targetUserId);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to switch to false bottom user", e);
        }
        return false;
    }

    /**
     * Called when biometric authentication succeeds on the lock screen.
     * Determines if false bottom routing is needed and initiates user switch if so.
     *
     * @param authenticatedUserId User whose biometric was authenticated
     * @param currentUserId Currently active foreground user
     * @return true if false bottom routing was applied (user switch initiated)
     */
    public boolean handleBiometricUnlock(
            @UserIdInt int authenticatedUserId,
            @UserIdInt int currentUserId) {

        BiometricRouteResult result = checkBiometricRoute(authenticatedUserId, currentUserId);

        if (result.shouldRoute) {
            return switchToFalseBottomUser(result.targetUserId);
        }

        return false;
    }
}
