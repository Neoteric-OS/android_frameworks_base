/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.timedetector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.time.ExternalTimeSuggestion;
import android.app.timedetector.GnssTimeSuggestion;
import android.app.timedetector.ManualTimeSuggestion;
import android.app.timedetector.NetworkTimeSuggestion;
import android.app.timedetector.TelephonyTimeSuggestion;
import android.util.IndentingPrintWriter;

class FakeTimeDetectorStrategy implements TimeDetectorStrategy {

    // Call tracking.
    private TelephonyTimeSuggestion mLastTelephonySuggestion;
    private ManualTimeSuggestion mLastManualSuggestion;
    private NetworkTimeSuggestion mLastNetworkSuggestion;
    private GnssTimeSuggestion mLastGnssSuggestion;
    private ExternalTimeSuggestion mLastExternalSuggestion;
    private boolean mDumpCalled;

    @Override
    public void suggestTelephonyTime(TelephonyTimeSuggestion timeSuggestion) {
        mLastTelephonySuggestion = timeSuggestion;
    }

    @Override
    public boolean suggestManualTime(ManualTimeSuggestion timeSuggestion) {
        mLastManualSuggestion = timeSuggestion;
        return true;
    }

    @Override
    public void suggestNetworkTime(NetworkTimeSuggestion timeSuggestion) {
        mLastNetworkSuggestion = timeSuggestion;
    }

    @Override
    public NetworkTimeSuggestion getLatestNetworkTimeSuggestion() {
        return mLastNetworkSuggestion;
    }

    @Override
    public void suggestGnssTime(GnssTimeSuggestion timeSuggestion) {
        mLastGnssSuggestion = timeSuggestion;
    }

    @Override
    public void suggestExternalTime(ExternalTimeSuggestion timeSuggestion) {
        mLastExternalSuggestion = timeSuggestion;
    }

    @Override
    public ConfigurationInternal getConfigurationInternal(int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dump(IndentingPrintWriter pw, String[] args) {
        mDumpCalled = true;
    }

    void resetCallTracking() {
        mLastTelephonySuggestion = null;
        mLastManualSuggestion = null;
        mLastNetworkSuggestion = null;
        mLastGnssSuggestion = null;
        mLastExternalSuggestion = null;
        mDumpCalled = false;
    }

    void verifySuggestTelephonyTimeCalled(TelephonyTimeSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastTelephonySuggestion);
    }

    void verifySuggestManualTimeCalled(ManualTimeSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastManualSuggestion);
    }

    void verifySuggestNetworkTimeCalled(NetworkTimeSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastNetworkSuggestion);
    }

    void verifySuggestGnssTimeCalled(GnssTimeSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastGnssSuggestion);
    }

    void verifySuggestExternalTimeCalled(ExternalTimeSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastExternalSuggestion);
    }

    void verifyDumpCalled() {
        assertTrue(mDumpCalled);
    }
}
