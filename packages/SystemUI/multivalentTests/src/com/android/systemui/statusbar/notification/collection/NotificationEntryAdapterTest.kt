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

package com.android.systemui.statusbar.notification.collection

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.mockNotificationActivityStarter
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.row.mockNotificationActionClickManager
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.statusbar.notification.stack.BUCKET_ALERTING
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class NotificationEntryAdapterTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory
    private lateinit var underTest: NotificationEntryAdapter

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getParent_adapter() {
        val ge = GroupEntryBuilder().build()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(ge)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.parent).isEqualTo(entry.parent)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isTopLevelEntry_adapter() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(GroupEntry.ROOT_ENTRY)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isTopLevelEntry).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getKey_adapter() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.key).isEqualTo(entry.key)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getRow_adapter() {
        val row = Mockito.mock(ExpandableNotificationRow::class.java)
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.row).isEqualTo(entry.row)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isGroupRoot_adapter_groupSummary() {
        val row = Mockito.mock(ExpandableNotificationRow::class.java)
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setGroupSummary(true)
                .setGroup("key")
                .build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(GroupEntry.ROOT_ENTRY)
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isGroupRoot).isFalse()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isGroupRoot_adapter_groupChild() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setGroupSummary(true)
                .setGroup("key")
                .build()

        val parent = NotificationEntryBuilder().setParent(GroupEntry.ROOT_ENTRY).build()
        val groupEntry = GroupEntryBuilder().setSummary(parent)

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(groupEntry.build())
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isGroupRoot).isFalse()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isClearable_adapter() {
        val row = Mockito.mock(ExpandableNotificationRow::class.java)
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isClearable).isEqualTo(entry.isClearable)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getSummarization_adapter() {
        val row = Mockito.mock(ExpandableNotificationRow::class.java)
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        val ranking = RankingBuilder(entry.ranking).setSummarization("hello").build()
        entry.setRanking(ranking)
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.summarization).isEqualTo("hello")
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getIcons_adapter() {
        val row = Mockito.mock(ExpandableNotificationRow::class.java)
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.icons).isEqualTo(entry.icons)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isColorized() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setColorized(true)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isColorized).isEqualTo(entry.sbn.notification.isColorized)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getSbn() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.sbn).isEqualTo(entry.sbn)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun canDragAndDrop() {
        val pi = Mockito.mock(PendingIntent::class.java)
        Mockito.`when`(pi.isActivity).thenReturn(true)
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setContentIntent(pi)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.canDragAndDrop()).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isBubble() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setFlag(Notification.FLAG_BUBBLE, true)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isBubbleCapable).isEqualTo(entry.isBubble)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getStyle() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setStyle(Notification.BigTextStyle())
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.style).isEqualTo(entry.notificationStyle)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getSectionBucket() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setStyle(Notification.BigTextStyle())
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        entry.bucket = BUCKET_ALERTING

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.sectionBucket).isEqualTo(BUCKET_ALERTING)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isAmbient() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setImportance(NotificationManager.IMPORTANCE_MIN)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isAmbient).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun canShowFullScreen() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setFullScreenIntent(Mockito.mock(PendingIntent::class.java), true)
                .build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setImportance(NotificationManager.IMPORTANCE_MIN)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isFullScreenCapable).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun onNotificationBubbleIconClicked() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onNotificationBubbleIconClicked()
        verify(kosmos.mockNotificationActivityStarter).onNotificationBubbleIconClicked(entry)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun onNotificationActionClicked() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        underTest.onNotificationActionClicked()
        verify(kosmos.mockNotificationActionClickManager).onNotificationActionClicked(entry)
    }
}
