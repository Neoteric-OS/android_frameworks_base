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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Client to support additional functionalities for selection menu. 
 * 
 * @hide Not part of the public API; only required by system implementors.
*/
@SystemApi
@FlaggedApi(android.webkit.Flags.FLAG_SET_PLATFORM_SELECTION_MENU_CLIENT)
public abstract class PlatformSelectionActionMenuClient {

  /**
   * Maps Chromium's default menu item order to platform-specific order. OEM should keep track of
   * SelectionActionMenuHelper#DefaultItemOrder which will be passed as params and keep custom
   * implementations in sync.
   *
   * @param order The Chromium menu item order constant from
   *     SelectionActionMenuHelper#DefaultItemOrder.
   * @return The corresponding platform menu item order constant, or -1 if no mapping exists
   */
  public int getPlatformDefaultMenuItemOrder(int order) {
    return -1;
  }

  /**
   * Gets additional text processing menu items supported by the platform.
   *
   * @return PlatformMenuData containing menu items that allows users to do registered text
   *     processing operations.
   */
  @Nullable
  public PlatformMenuData getAdditionalTextProcessingItems() {
    return null;
  }

  /**
   * Filters text processing activities based on user preferences from settings. OEM should not add
   * extra ResolveInfo's into the returned list and should only remove items from the input list.
   *
   * @param context The application context
   * @param activities The list of ResolveInfo objects representing available text processing
   *     activities
   * @return Filtered list containing only activities enabled in settings, or empty list if 
   *     none are enabled or user hasn't configured preferences
   */
  @NonNull
  public List<ResolveInfo> filterTextProcessingActivities(
      @NonNull Context context, @NonNull List<ResolveInfo> activities) {
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
  @NonNull
  public List<PlatformMenuData> getAdditionalMenuItems(
      @NonNull Context context,
      boolean isSelectionPassword,
      boolean isSelectionReadOnly,
      @NonNull String selectedText) {
    return new ArrayList<>();
  }

  /**
   * Handles click events for platform-specific menu items. Chromium implementation checks for
   * {@link PlatformSelectionActionMenuClient#canHandleCustomMenuItem(MenuItem)}} before invoking
   * this method.
   *
   * @param context The application context
   * @param item The menu item that was clicked
   * @param containerView The view containing the selection
   * @param isFocusedNodeEditable Focused node is editable or not.
   * @return true if the click was handled, false otherwise.
   */
  public boolean handleMenuItemClick(
      @NonNull Context context,
      @NonNull MenuItem item,
      @NonNull ViewGroup containerView,
      boolean isFocusedNodeEditable) {
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

    @Nullable
    public CharSequence getTitle() {
      return mTitle;
    }

    public int getTitleRes() {
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

    @Nullable
    public View.OnClickListener getClickListener() {
      return mClickListener;
    }

    @Nullable
    public Intent createMenuIntent() {
      return mIntent;
    }

    public boolean needsOnlyOrderingChange() {
      return mOnlyNeedOrderingChange;
    }
  }
}
