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

package com.android.systemui.statusbar.notification.collection;

import static android.app.NotificationChannel.NEWS_ID;
import static android.app.NotificationChannel.PROMOTIONS_ID;
import static android.app.NotificationChannel.RECS_ID;
import static android.app.NotificationChannel.SOCIAL_MEDIA_ID;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.statusbar.notification.icon.IconPack;
import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

import java.util.List;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * Class to represent notifications bundled by classification.
 */
public class BundleEntry extends PipelineEntry {

    private final BundleEntryAdapter mEntryAdapter;

    // TODO(b/394483200): move NotificationEntry's implementation to PipelineEntry?
    private final MutableStateFlow<Boolean> mSensitive = StateFlowKt.MutableStateFlow(false);

    // TODO (b/389839319): implement the row
    private ExpandableNotificationRow mRow;

    public BundleEntry(String key) {
        super(key);
        mEntryAdapter = new BundleEntryAdapter();
    }

    @Nullable
    @Override
    public NotificationEntry getRepresentativeEntry() {
        return null;
    }

    @Nullable
    @Override
    public NotifSection getSection() {
        return null;
    }

    @Override
    public int getSectionIndex() {
        return 0;
    }

    @Nullable
    @Override
    public PipelineEntry getParent() {
        return null;
    }

    @Override
    public boolean wasAttachedInPreviousPass() {
        return false;
    }

    @VisibleForTesting
    public BundleEntryAdapter getEntryAdapter() {
        return mEntryAdapter;
    }

    public class BundleEntryAdapter implements EntryAdapter {

        /**
         * TODO (b/394483200): convert to PipelineEntry.ROOT_ENTRY when pipeline is migrated?
         */
        @Override
        public GroupEntry getParent() {
            return GroupEntry.ROOT_ENTRY;
        }

        @Override
        public boolean isTopLevelEntry() {
            return true;
        }

        @NonNull
        @Override
        public String getKey() {
            return mKey;
        }

        @Override
        @Nullable
        public ExpandableNotificationRow getRow() {
            return mRow;
        }

        @Override
        public boolean isGroupRoot() {
            return true;
        }

        @Override
        public StateFlow<Boolean> isSensitive() {
            return BundleEntry.this.mSensitive;
        }

        @Override
        public boolean isClearable() {
            // TODO(b/394483200): check whether all of the children are clearable, when implemented
            return true;
        }

        @Override
        public int getTargetSdk() {
            return Build.VERSION_CODES.CUR_DEVELOPMENT;
        }

        @Override
        public String getSummarization() {
            return null;
        }

        @Override
        public int getContrastedColor(Context context, boolean isLowPriority, int backgroundColor) {
            return Notification.COLOR_DEFAULT;
        }

        @Override
        public boolean canPeek() {
            return false;
        }

        @Override
        public long getWhen() {
            return 0;
        }

        @Override
        public IconPack getIcons() {
            // TODO(b/396446620): implement bundle icons
            return null;
        }

        @Override
        public boolean isColorized() {
            return false;
        }

        @Override
        @Nullable
        public StatusBarNotification getSbn() {
            return null;
        }

        @Override
        public boolean canDragAndDrop() {
            return false;
        }

        @Override
        public boolean isBubbleCapable() {
            return false;
        }

        @Override
        @Nullable
        public String getStyle() {
            return null;
        }

        @Override
        public int getSectionBucket() {
            return mBucket;
        }

        @Override
        public boolean isAmbient() {
            return false;
        }

        @Override
        public boolean isFullScreenCapable() {
            return false;
        }
    }

    public static final List<BundleEntry> ROOT_BUNDLES = List.of(
            new BundleEntry(PROMOTIONS_ID),
            new BundleEntry(SOCIAL_MEDIA_ID),
            new BundleEntry(NEWS_ID),
            new BundleEntry(RECS_ID));
}
