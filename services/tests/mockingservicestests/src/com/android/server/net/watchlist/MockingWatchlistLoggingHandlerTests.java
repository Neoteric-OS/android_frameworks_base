/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.net.watchlist;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.DropBoxManager;
import android.os.Looper;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class MockingWatchlistLoggingHandlerTests {
    private Context mSpyContext;
    private MockitoSession mMockitoSession;

    @Mock
    private WatchlistReportDbHelper mMockDbHelper;
    @Mock
    private DropBoxManager mMockDropBoxManager;
    @Mock
    private WatchlistSettings mSettings;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mMockitoSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(Settings.Global.class)
                .mockStatic(WatchlistReportDbHelper.class)
                .mockStatic(WatchlistSettings.class)
                .startMocking();

        mSpyContext = spy(InstrumentationRegistry.getContext());

        when(mSpyContext.getSystemService(DropBoxManager.class)).thenReturn(mMockDropBoxManager);
        when(mMockDropBoxManager.isTagEnabled(WatchlistLoggingHandler.DROPBOX_TAG)).thenReturn(true);
        when(WatchlistReportDbHelper.getInstance(any())).thenReturn(mMockDbHelper);
        when(WatchlistSettings.getInstance()).thenReturn(mSettings);
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testWatchlistLoggingHandler_tryAggregateRecordsOfEmptyDatabase() throws Exception {
        doReturn(0L).when(() -> Settings.Global.getLong(
                any(), eq(Settings.Global.NETWORK_WATCHLIST_LAST_REPORT_TIME), anyLong()));

        final WatchlistReportDbHelper.AggregatedResult
                aggregatedResult = new WatchlistReportDbHelper.AggregatedResult(
                        new HashSet<>(), null, new HashMap<>());
        when(mMockDbHelper.getAggregatedRecords(anyLong())).thenReturn(aggregatedResult);

        WatchlistLoggingHandler loggingHandler = spy(
                new WatchlistLoggingHandler(mSpyContext, Looper.getMainLooper()));
        loggingHandler.tryAggregateRecords(WatchlistLoggingHandler.getLastMidnightTime());

        verify(loggingHandler, never()).getAllDigestsForReport(any());
    }
}
