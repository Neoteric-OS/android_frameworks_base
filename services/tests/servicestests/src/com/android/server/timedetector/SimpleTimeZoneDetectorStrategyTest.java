/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.timedetector.TimeSignal;
import android.content.Intent;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.util.TimestampedValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

@RunWith(JUnitParamsRunner.class)
public class SimpleTimeZoneDetectorStrategyTest {

    private static final Scenario SCENARIO_1 = new Scenario.Builder()
            .setInitialDeviceSystemClockUtc(1977, 1, 1, 12, 0, 0)
            .setInitialDeviceRealtimeMillis(123456789L)
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .build();

    private Script mScript;

    @Before
    public void setUp() {
        mScript = new Script();
    }

    @Parameters(method = "validSources")
    @Test
    public void testSuggestTime_timeDetectionEnabled(String sourceId) {
        Scenario scenario = SCENARIO_1;
        mScript.pokeFakeClocks(scenario)
                .pokeTimeDetectionEnabled(true);

        TimeSignal timeSignal = scenario.createTimeSignalForActual(sourceId);
        final int clockIncrement = 1000;
        long expectSystemClockMillis = scenario.getActualTimeMillis() + clockIncrement;

        mScript.simulateTimePassing(clockIncrement)
                .simulateTimeSignalReceived(timeSignal)
                .verifySystemClockWasSetAndResetCallTracking(expectSystemClockMillis, sourceId);
    }

    @Parameters(method = "validSources")
    @Test
    public void testSuggestTime_systemClockThreshold(String sourceId) {
        Scenario scenario = SCENARIO_1;
        final int systemClockUpdateThresholdMillis = 1000;
        mScript.pokeFakeClocks(scenario)
                .pokeThresholds(systemClockUpdateThresholdMillis)
                .pokeTimeDetectionEnabled(true);

        TimeSignal timeSignal1 = scenario.createTimeSignalForActual(sourceId);
        TimestampedValue<Long> utcTime1 = timeSignal1.getUtcTime();

        final int clockIncrement = 100;
        // Increment the the device clocks to simulate the passage of time.
        mScript.simulateTimePassing(clockIncrement);

        long expectSystemClockMillis1 =
                TimeDetectorStrategy.getTimeAt(utcTime1, mScript.peekElapsedRealtimeMillis());

        // Send the first time signal. It should be used.
        mScript.simulateTimeSignalReceived(timeSignal1)
                .verifySystemClockWasSetAndResetCallTracking(expectSystemClockMillis1, sourceId);

        // Now send another time signal, but one that is too similar to the last one and should be
        // ignored.
        int underThresholdMillis = systemClockUpdateThresholdMillis - 1;
        TimestampedValue<Long> utcTime2 = new TimestampedValue<>(
                mScript.peekElapsedRealtimeMillis(),
                mScript.peekSystemClockMillis() + underThresholdMillis);
        TimeSignal timeSignal2 = new TimeSignal(sourceId, utcTime2);
        mScript.simulateTimePassing(clockIncrement)
                .simulateTimeSignalReceived(timeSignal2)
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Now send another time signal, but one that is on the threshold and so should be used.
        TimestampedValue<Long> utcTime3 = new TimestampedValue<>(
                mScript.peekElapsedRealtimeMillis(),
                mScript.peekSystemClockMillis() + systemClockUpdateThresholdMillis);

        TimeSignal timeSignal3 = new TimeSignal(sourceId, utcTime3);
        mScript.simulateTimePassing(clockIncrement);

        long expectSystemClockMillis3 =
                TimeDetectorStrategy.getTimeAt(utcTime3, mScript.peekElapsedRealtimeMillis());

        mScript.simulateTimeSignalReceived(timeSignal3)
                .verifySystemClockWasSetAndResetCallTracking(expectSystemClockMillis3, sourceId);
    }

    @Parameters(method = "validSources")
    @Test
    public void testSuggestTime_timeDetectionDisabled(String sourceId) {
        Scenario scenario = SCENARIO_1;
        mScript.pokeFakeClocks(scenario)
                .pokeTimeDetectionEnabled(false);

        TimeSignal timeSignal = scenario.createTimeSignalForActual(sourceId);
        mScript.simulateTimeSignalReceived(timeSignal)
                .verifySystemClockWasNotSetAndResetCallTracking();
    }

    @Test
    public void testSuggestTime_nitz_invalidNitzReferenceTimesIgnored() {
        Scenario scenario = SCENARIO_1;
        final String sourceId = TimeSignal.SOURCE_ID_NITZ;

        final int systemClockUpdateThreshold = 2000;
        mScript.pokeFakeClocks(scenario)
                .pokeThresholds(systemClockUpdateThreshold)
                .pokeTimeDetectionEnabled(true);
        TimeSignal timeSignal1 = scenario.createTimeSignalForActual(sourceId);
        TimestampedValue<Long> utcTime1 = timeSignal1.getUtcTime();

        // Initialize the strategy / device with a time set from NITZ.
        mScript.simulateTimePassing(100);
        long expectedSystemClockMillis1 =
                TimeDetectorStrategy.getTimeAt(utcTime1, mScript.peekElapsedRealtimeMillis());
        mScript.simulateTimeSignalReceived(timeSignal1)
                .verifySystemClockWasSetAndResetCallTracking(expectedSystemClockMillis1, sourceId);

        // The UTC time increment should be larger than the system clock update threshold so we
        // know it shouldn't be ignored for other reasons.
        long validUtcTimeMillis = utcTime1.getValue() + (2 * systemClockUpdateThreshold);

        // Now supply a new signal that has an obviously bogus reference time : older than the last
        // one.
        long referenceTimeBeforeLastSignalMillis = utcTime1.getReferenceTimeMillis() - 1;
        TimestampedValue<Long> utcTime2 = new TimestampedValue<>(
                referenceTimeBeforeLastSignalMillis, validUtcTimeMillis);
        TimeSignal timeSignal2 = new TimeSignal(sourceId, utcTime2);
        mScript.simulateTimeSignalReceived(timeSignal2)
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Now supply a new signal that has an obviously bogus reference time : substantially in the
        // future.
        long referenceTimeInFutureMillis =
                utcTime1.getReferenceTimeMillis() + Integer.MAX_VALUE + 1;
        TimestampedValue<Long> utcTime3 = new TimestampedValue<>(
                referenceTimeInFutureMillis, validUtcTimeMillis);
        TimeSignal timeSignal3 = new TimeSignal(sourceId, utcTime3);
        mScript.simulateTimeSignalReceived(timeSignal3)
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Just to prove validUtcTimeMillis is valid.
        long validReferenceTimeMillis = utcTime1.getReferenceTimeMillis() + 100;
        TimestampedValue<Long> utcTime4 = new TimestampedValue<>(
                validReferenceTimeMillis, validUtcTimeMillis);
        long expectedSystemClockMillis4 =
                TimeDetectorStrategy.getTimeAt(utcTime4, mScript.peekElapsedRealtimeMillis());
        TimeSignal timeSignal4 = new TimeSignal(sourceId, utcTime4);
        mScript.simulateTimeSignalReceived(timeSignal4)
                .verifySystemClockWasSetAndResetCallTracking(expectedSystemClockMillis4, sourceId);
    }

    @Parameters(method = "validSources")
    @Test
    public void testSuggestTime_timeDetectionToggled(String sourceId) {
        Scenario scenario = SCENARIO_1;
        final int clockIncrementMillis = 100;
        final int systemClockUpdateThreshold = 2000;
        mScript.pokeFakeClocks(scenario)
                .pokeThresholds(systemClockUpdateThreshold)
                .pokeTimeDetectionEnabled(false);

        TimeSignal timeSignal1 = scenario.createTimeSignalForActual(sourceId);
        TimestampedValue<Long> utcTime1 = timeSignal1.getUtcTime();

        // Simulate time passing.
        mScript.simulateTimePassing(clockIncrementMillis);

        // Simulate the time signal being received. It should not be used because auto time
        // detection is off but it should be recorded.
        mScript.simulateTimeSignalReceived(timeSignal1)
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Simulate more time passing.
        mScript.simulateTimePassing(clockIncrementMillis);

        long expectedSystemClockMillis1 =
                TimeDetectorStrategy.getTimeAt(utcTime1, mScript.peekElapsedRealtimeMillis());

        // Turn on auto time detection.
        mScript.simulateAutoTimeDetectionToggle()
                .verifySystemClockWasSetAndResetCallTracking(expectedSystemClockMillis1, sourceId);

        // Turn off auto time detection.
        mScript.simulateAutoTimeDetectionToggle()
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Receive another valid time signal.
        // It should be on the threshold and accounting for the clock increments.
        TimestampedValue<Long> utcTime2 = new TimestampedValue<>(
                mScript.peekElapsedRealtimeMillis(),
                mScript.peekSystemClockMillis() + systemClockUpdateThreshold);
        TimeSignal timeSignal2 = new TimeSignal(sourceId, utcTime2);

        // Simulate more time passing.
        mScript.simulateTimePassing(clockIncrementMillis);

        long expectedSystemClockMillis2 =
                TimeDetectorStrategy.getTimeAt(utcTime2, mScript.peekElapsedRealtimeMillis());

        // The new time, though valid, should not be set in the system clock because auto time is
        // disabled.
        mScript.simulateTimeSignalReceived(timeSignal2)
                .verifySystemClockWasNotSetAndResetCallTracking();

        // Turn on auto time detection.
        mScript.simulateAutoTimeDetectionToggle()
                .verifySystemClockWasSetAndResetCallTracking(expectedSystemClockMillis2, sourceId);
    }

    // Confirms that NITZ and NTP are not currently prioritized so either will be taken over the
    // other if suggested. This may not be the eventual behavior but it is intended right now.
    @Test
    public void testSuggestTime_noPrioritization() {
        Scenario scenario = SCENARIO_1;
        mScript.pokeFakeClocks(scenario)
                .pokeTimeDetectionEnabled(true);

        String[] sourceIdOrder = {
                TimeSignal.SOURCE_ID_NITZ, TimeSignal.SOURCE_ID_NTP,
                TimeSignal.SOURCE_ID_NITZ, TimeSignal.SOURCE_ID_NTP,
        };

        final int clockIncrement = 1000;
        long actualTimeMillis = scenario.getActualTimeMillis();
        for (String sourceId : sourceIdOrder) {
            actualTimeMillis += TimeUnit.DAYS.toMillis(1);
            long expectSystemClockMillis = actualTimeMillis + clockIncrement;

            TimestampedValue<Long> utcTime = new TimestampedValue<>(
                    mScript.peekElapsedRealtimeMillis(), actualTimeMillis);
            TimeSignal timeSignal = new TimeSignal(sourceId, utcTime);
            mScript.simulateTimePassing(clockIncrement)
                    .simulateTimeSignalReceived(timeSignal)
                    .verifySystemClockWasSetAndResetCallTracking(expectSystemClockMillis, sourceId);
        }
    }

    @Test
    public void testSuggestTime_unknownSource() {
        Scenario scenario = SCENARIO_1;
        mScript.pokeFakeClocks(scenario)
                .pokeTimeDetectionEnabled(true);

        TimeSignal timeSignal = scenario.createTimeSignalForActual("unknown");
        mScript.simulateTimeSignalReceived(timeSignal)
                .verifySystemClockWasNotSetAndResetCallTracking();
    }

    /**
     * A fake implementation of TimeDetectorStrategy.Callback. Besides tracking changes and behaving
     * like the real thing should, it also asserts preconditions.
     */
    private static class FakeCallback implements TimeDetectorStrategy.Callback {
        private boolean mTimeDetectionEnabled;
        private boolean mWakeLockAcquired;
        private long mElapsedRealtimeMillis;
        private long mSystemClockMillis;
        private int mSystemClockUpdateThresholdMillis = 2000;

        // Tracking operations.
        private boolean mSystemClockWasSet;
        private Intent mBroadcastSent;

        @Override
        public int systemClockUpdateThresholdMillis() {
            return mSystemClockUpdateThresholdMillis;
        }

        @Override
        public boolean isTimeDetectionEnabled() {
            return mTimeDetectionEnabled;
        }

        @Override
        public void acquireWakeLock() {
            if (mWakeLockAcquired) {
                fail("Wake lock already acquired");
            }
            mWakeLockAcquired = true;
        }

        @Override
        public long elapsedRealtimeMillis() {
            assertWakeLockAcquired();
            return mElapsedRealtimeMillis;
        }

        @Override
        public long systemClockMillis() {
            assertWakeLockAcquired();
            return mSystemClockMillis;
        }

        @Override
        public void setSystemClock(long newTimeMillis) {
            assertWakeLockAcquired();
            mSystemClockWasSet = true;
            mSystemClockMillis = newTimeMillis;
        }

        @Override
        public void releaseWakeLock() {
            assertWakeLockAcquired();
            mWakeLockAcquired = false;
        }

        @Override
        public void sendStickyBroadcast(Intent intent) {
            assertNotNull(intent);
            mBroadcastSent = intent;
        }

        // Methods below are for managing the fake's behavior.

        public void pokeSystemClockUpdateThreshold(int thresholdMillis) {
            mSystemClockUpdateThresholdMillis = thresholdMillis;
        }

        public void pokeElapsedRealtimeMillis(long elapsedRealtimeMillis) {
            mElapsedRealtimeMillis = elapsedRealtimeMillis;
        }

        public void pokeSystemClockMillis(long systemClockMillis) {
            mSystemClockMillis = systemClockMillis;
        }

        public void pokeTimeDetectionEnabled(boolean enabled) {
            mTimeDetectionEnabled = enabled;
        }

        public long peekElapsedRealtimeMillis() {
            return mElapsedRealtimeMillis;
        }

        public long peekSystemClockMillis() {
            return mSystemClockMillis;
        }

        public void simulateTimePassing(int incrementMillis) {
            mElapsedRealtimeMillis += incrementMillis;
            mSystemClockMillis += incrementMillis;
        }

        public void verifySystemClockNotSet() {
            assertFalse(mSystemClockWasSet);
        }

        public void verifySystemClockWasSet(long expectSystemClockMillis) {
            assertTrue(mSystemClockWasSet);
            assertEquals(expectSystemClockMillis, mSystemClockMillis);
        }

        public void verifyIntentWasBroadcast() {
            assertTrue(mBroadcastSent != null);
        }

        public void verifyIntentWasNotBroadcast() {
            assertNull(mBroadcastSent);
        }

        public void resetCallTracking() {
            mSystemClockWasSet = false;
            mBroadcastSent = null;
        }

        private void assertWakeLockAcquired() {
            assertTrue("The operation must be performed only after acquiring the wakelock",
                    mWakeLockAcquired);
        }
    }

    /**
     * A fluent helper class for tests.
     */
    private class Script {

        private final FakeCallback mFakeCallback;
        private final SimpleTimeDetectorStrategy mSimpleTimeDetectorStrategy;

        public Script() {
            mFakeCallback = new FakeCallback();
            mSimpleTimeDetectorStrategy = new SimpleTimeDetectorStrategy();
            mSimpleTimeDetectorStrategy.initialize(mFakeCallback);

        }

        Script pokeTimeDetectionEnabled(boolean enabled) {
            mFakeCallback.pokeTimeDetectionEnabled(enabled);
            return this;
        }

        Script pokeFakeClocks(Scenario scenario) {
            mFakeCallback.pokeElapsedRealtimeMillis(scenario.getInitialRealTimeMillis());
            mFakeCallback.pokeSystemClockMillis(scenario.getInitialSystemClockMillis());
            return this;
        }

        Script pokeThresholds(int systemClockUpdateThreshold) {
            mFakeCallback.pokeSystemClockUpdateThreshold(systemClockUpdateThreshold);
            return this;
        }

        long peekElapsedRealtimeMillis() {
            return mFakeCallback.peekElapsedRealtimeMillis();
        }

        long peekSystemClockMillis() {
            return mFakeCallback.peekSystemClockMillis();
        }

        Script simulateTimeSignalReceived(TimeSignal timeSignal) {
            mSimpleTimeDetectorStrategy.suggestTime(timeSignal);
            return this;
        }

        Script simulateAutoTimeDetectionToggle() {
            boolean enabled = !mFakeCallback.isTimeDetectionEnabled();
            mFakeCallback.pokeTimeDetectionEnabled(enabled);
            mSimpleTimeDetectorStrategy.handleAutoTimeDetectionToggle(enabled);
            return this;
        }

        Script simulateTimePassing(int clockIncrement) {
            mFakeCallback.simulateTimePassing(clockIncrement);
            return this;
        }

        Script verifySystemClockWasNotSetAndResetCallTracking() {
            mFakeCallback.verifySystemClockNotSet();
            mFakeCallback.verifyIntentWasNotBroadcast();
            mFakeCallback.resetCallTracking();
            return this;
        }

        Script verifySystemClockWasSetAndResetCallTracking(
                long expectSystemClockMillis, String sourceId) {
            mFakeCallback.verifySystemClockWasSet(expectSystemClockMillis);

            // TODO Remove this when the broadcast is removed.
            if (TimeSignal.SOURCE_ID_NITZ.equals(sourceId)) {
                mFakeCallback.verifyIntentWasBroadcast();
            }
            mFakeCallback.resetCallTracking();
            return this;
        }
    }

    /**
     * A starting scenario used during tests. Describes a fictional "physical" reality.
     */
    private static class Scenario {

        private final long mInitialDeviceSystemClockMillis;
        private final long mInitialDeviceRealtimeMillis;
        private final long mActualTimeMillis;

        Scenario(long initialDeviceSystemClock, long elapsedRealtime, long timeMillis) {
            mInitialDeviceSystemClockMillis = initialDeviceSystemClock;
            mActualTimeMillis = timeMillis;
            mInitialDeviceRealtimeMillis = elapsedRealtime;
        }

        long getInitialRealTimeMillis() {
            return mInitialDeviceRealtimeMillis;
        }

        long getInitialSystemClockMillis() {
            return mInitialDeviceSystemClockMillis;
        }

        long getActualTimeMillis() {
            return mActualTimeMillis;
        }

        TimeSignal createTimeSignalForActual(String sourceId) {
            TimestampedValue<Long> time = new TimestampedValue<>(
                    mInitialDeviceRealtimeMillis, mActualTimeMillis);
            return new TimeSignal(sourceId, time);
        }

        static class Builder {

            private long mInitialDeviceSystemClockMillis;
            private long mInitialDeviceRealtimeMillis;
            private long mActualTimeMillis;

            Builder setInitialDeviceSystemClockUtc(int year, int monthInYear, int day,
                    int hourOfDay, int minute, int second) {
                mInitialDeviceSystemClockMillis = createUtcTime(year, monthInYear, day, hourOfDay,
                        minute, second);
                return this;
            }

            Builder setInitialDeviceRealtimeMillis(long realtimeMillis) {
                mInitialDeviceRealtimeMillis = realtimeMillis;
                return this;
            }

            Builder setActualTimeUtc(int year, int monthInYear, int day, int hourOfDay,
                    int minute, int second) {
                mActualTimeMillis =
                        createUtcTime(year, monthInYear, day, hourOfDay, minute, second);
                return this;
            }

            Scenario build() {
                return new Scenario(mInitialDeviceSystemClockMillis, mInitialDeviceRealtimeMillis,
                        mActualTimeMillis);
            }
        }
    }

    private static long createUtcTime(int year, int monthInYear, int day, int hourOfDay, int minute,
            int second) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("Etc/UTC"));
        cal.clear();
        cal.set(year, monthInYear - 1, day, hourOfDay, minute, second);
        return cal.getTimeInMillis();
    }

    /** Used with @Parameters to provide valid source IDs */
    @SuppressWarnings("unused")
    private static String[] validSources() {
        return new String[] {
                TimeSignal.SOURCE_ID_NITZ,
                TimeSignal.SOURCE_ID_NTP,
        };
    }
}
