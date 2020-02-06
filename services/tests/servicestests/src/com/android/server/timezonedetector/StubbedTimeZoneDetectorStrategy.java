/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.server.timezonedetector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.timezonedetector.GeolocationTimeZoneSuggestion;
import android.app.timezonedetector.ManualTimeZoneSuggestion;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;

import java.io.PrintWriter;

class StubbedTimeZoneDetectorStrategy implements TimeZoneDetectorStrategy {

    // Call tracking.
    private GeolocationTimeZoneSuggestion mLastGeolocationSuggestion;
    private ManualTimeZoneSuggestion mLastManualSuggestion;
    private TelephonyTimeZoneSuggestion mLastTelephonySuggestion;
    private boolean mHandleAutoTimeZoneDetectionChangedCalled;
    private boolean mDumpCalled;

    @Override
    public void suggestGeolocationTimeZone(GeolocationTimeZoneSuggestion timeZoneSuggestion) {
        mLastGeolocationSuggestion = timeZoneSuggestion;
    }

    @Override
    public void suggestManualTimeZone(ManualTimeZoneSuggestion timeZoneSuggestion) {
        mLastManualSuggestion = timeZoneSuggestion;
    }

    @Override
    public void suggestTelephonyTimeZone(TelephonyTimeZoneSuggestion timeZoneSuggestion) {
        mLastTelephonySuggestion = timeZoneSuggestion;
    }

    @Override
    public void handleAutoTimeZoneDetectionChanged() {
        mHandleAutoTimeZoneDetectionChangedCalled = true;
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        mDumpCalled = true;
    }

    void resetCallTracking() {
        mLastGeolocationSuggestion = null;
        mLastManualSuggestion = null;
        mLastTelephonySuggestion = null;
        mHandleAutoTimeZoneDetectionChangedCalled = false;
        mDumpCalled = false;
    }

    void verifySuggestGeolocationTimeZoneCalled(
            GeolocationTimeZoneSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastGeolocationSuggestion);
    }

    void verifySuggestManualTimeZoneCalled(ManualTimeZoneSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastManualSuggestion);
    }

    void verifySuggestTelephonyTimeZoneCalled(TelephonyTimeZoneSuggestion expectedSuggestion) {
        assertEquals(expectedSuggestion, mLastTelephonySuggestion);
    }

    void verifyHandleAutoTimeZoneDetectionChangedCalled() {
        assertTrue(mHandleAutoTimeZoneDetectionChangedCalled);
    }

    void verifyDumpCalled() {
        assertTrue(mDumpCalled);
    }
}
