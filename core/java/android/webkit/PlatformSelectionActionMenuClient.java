/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.webkit;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Client to support additional functionalities for selection menu. */
public abstract class PlatformSelectionActionMenuClient {

    /**
     * Maps Chromium's default menu item order to platform-specific order.
     *
     * @param order The Chromium menu item order constant from InputDefaultItemOrder
     * @return The corresponding platform menu item order constant, or -1 if no mapping exists
     */
    public int getPlatformDefaultMenuItemOrder(int order) {
        return -1;
    }

    /**
     * Gets additional text processing menu items supported by the platform.
     *
     * @return PlatformMenuData containing the "Manage Apps" menu item that allows users to
     *     configure text processing applications
     */
    public PlatformMenuData getAdditionalTextProcessingItems() {
        return null;
    }

    /**
     * Filters text processing activities based on user preferences from settings.
     *
     * @param context The application context
     * @param activities The list of ResolveInfo objects representing available text processing
     *     activities
     * @return Filtered list containing only activities enabled in settings, or empty list if none
     *     are enabled or user hasn't configured preferences
     */
    public List<ResolveInfo> filterTextProcessingActivities(
            Context context, List<ResolveInfo> activities) {
        return activities;
    }

    /**
     * Gets platform-specific additional menu items for text selection.
     *
     * @param context The application context
     * @param isSelectionPassword Whether the selected text is from a password field
     * @param isSelectionReadOnly Whether the selected text is from a read-only field
     * @param selectedText The currently selected text
     * @return List of PlatformMenuData objects representing additional menu items
     */
    public List<PlatformMenuData> getAdditionalMenuItems(
            Context context,
            boolean isSelectionPassword,
            boolean isSelectionReadOnly,
            String selectedText) {
        return new ArrayList<>();
    }

    /**
     * Checks whether a cached selection menu can be reused.
     *
     * @param context The application context
     * @return true if the menu can be reused, false if the menu needs to be rebuilt
     */
    public boolean canReuseCachedSelectionMenu(Context context) {
        return true;
    }

    /**
     * Checks if a menu item is a platform-specific custom item that can be handled.
     *
     * @param item The menu item to check
     * @return true if the item is a platform-specific menu item, false otherwise
     */
    public boolean canHandleCustomMenuItem(MenuItem item) {
        return false;
    }

    /**
     * Prepares the system for handling platform-specific menu items.
     *
     * @param context The application context
     * @param containerView The view containing the selection
     */
    public void prepareForMenuHandling(Context context, View containerView) {}

    /**
     * Handles click events for platform-specific menu items.
     *
     * @param context The application context
     * @param item The menu item that was clicked
     * @param containerView The view containing the selection
     * @return true if the click was handled, false otherwise
     */
    public boolean handleMenuItemClick(Context context, MenuItem item, ViewGroup containerView) {
        return false;
    }

    /**
     * Represents platform-specific menu data for selection actions. Contains all necessary
     * information to create and handle menu items.
     */
    public class PlatformMenuData {
        private final CharSequence mTitle;
        private final int mTitleResId;
        private final int mId;
        private final int mIconAttr;
        private final int mOrderInCategory;
        private final View.OnClickListener mClickListener;
        private final Intent mIntent;
        private final boolean mOnlyNeedOrderingChange;

        /** Constructs PlatformMenuData with a CharSequence title. */
        PlatformMenuData(
                CharSequence title,
                int id,
                int icon,
                int order,
                View.OnClickListener clicklistener,
                Intent intent,
                boolean onlyNeedOrderingChange) {
            mTitle = title;
            mTitleResId = 0;
            mId = id;
            mIconAttr = icon;
            mOrderInCategory = order;
            mClickListener = clicklistener;
            mIntent = intent;
            mOnlyNeedOrderingChange = onlyNeedOrderingChange;
        }

        /** Constructs PlatformMenuData with a title resource ID. */
        PlatformMenuData(
                int titleResId,
                int id,
                int icon,
                int order,
                View.OnClickListener clicklistener,
                Intent intent,
                boolean onlyNeedOrderingChange) {
            mTitleResId = titleResId;
            mTitle = null;
            mId = id;
            mIconAttr = icon;
            mOrderInCategory = order;
            mClickListener = clicklistener;
            mIntent = intent;
            mOnlyNeedOrderingChange = onlyNeedOrderingChange;
        }

        public CharSequence getTitle() {
            return mTitle;
        }

        public int getTitleres() {
            return mTitleResId;
        }

        public int getId() {
            return mId;
        }

        public int getIconAttr() {
            return mIconAttr;
        }

        public int getOrderInCategory() {
            return mOrderInCategory;
        }

        public View.OnClickListener getClickListener() {
            return mClickListener;
        }

        public Intent createMenuIntent() {
            return mIntent;
        }

        public boolean needsOnlyOrderingChange() {
            return mOnlyNeedOrderingChange;
        }
    }
}
