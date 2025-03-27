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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * Class to represent notifications bundled by classification.
 */
public class BundleEntry extends PipelineEntry {

    // TODO(b/394483200): move NotificationEntry's implementation to PipelineEntry?
    private final MutableStateFlow<Boolean> mSensitive = StateFlowKt.MutableStateFlow(false);

    // TODO (b/389839319): implement the row
    private ExpandableNotificationRow mRow;

    private final List<ListEntry> mChildren = new ArrayList<>();

    private final List<ListEntry> mUnmodifiableChildren = Collections.unmodifiableList(mChildren);

    public BundleEntry(String key) {
        super(key);
    }

    void addChild(ListEntry child) {
        mChildren.add(child);
    }

    @NonNull
    public List<ListEntry> getChildren() {
        return mUnmodifiableChildren;
    }

    /**
     * @return Null because bundles do not have an associated NotificationEntry.
     */

    @Nullable
    @Override
    public NotificationEntry getRepresentativeEntry() {
        return null;
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

    @Nullable
    public ExpandableNotificationRow getRow() {
        return mRow;
    }

    public static final List<BundleEntry> ROOT_BUNDLES = List.of(
            new BundleEntry(PROMOTIONS_ID),
            new BundleEntry(SOCIAL_MEDIA_ID),
            new BundleEntry(NEWS_ID),
            new BundleEntry(RECS_ID));

    public MutableStateFlow<Boolean> isSensitive() {
        return mSensitive;
    }
}
