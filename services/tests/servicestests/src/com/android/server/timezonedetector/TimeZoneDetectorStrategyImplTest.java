/*
 * Copyright 2019 The Android Open Source Project
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

import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_EMULATOR_ZONE_ID;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_ONLY;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_SINGLE_ZONE;

import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.AUTO_MODE_GEOLOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.AUTO_MODE_TELEPHONY;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_HIGH;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_HIGHEST;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_LOW;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_MEDIUM;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_NONE;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.TELEPHONY_SCORE_USAGE_THRESHOLD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.timezonedetector.GeolocationTimeZoneSuggestion;
import android.app.timezonedetector.ManualTimeZoneSuggestion;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion.MatchType;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion.Quality;

import com.android.server.timezonedetector.TimeZoneDetectorStrategy.AutoMode;
import com.android.server.timezonedetector.TimeZoneDetectorStrategyImpl.QualifiedTelephonyTimeZoneSuggestion;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

/**
 * White-box unit tests for {@link TimeZoneDetectorStrategyImpl}.
 */
public class TimeZoneDetectorStrategyImplTest {

    /** A time zone used for initialization that does not occur elsewhere in tests. */
    private static final String ARBITRARY_TIME_ZONE_ID = "Etc/UTC";
    private static final int SLOT_INDEX1 = 10000;
    private static final int SLOT_INDEX2 = 20000;

    // Telephony suggestion test cases are ordered so that each successive one is of the same or
    // higher score than the previous.
    private static final TelephonyTestCase[] TELEPHONY_TEST_CASES = new TelephonyTestCase[] {
            newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_ONLY,
                    QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS, TELEPHONY_SCORE_LOW),
            newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_ONLY,
                    QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, TELEPHONY_SCORE_MEDIUM),
            newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET,
                    QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, TELEPHONY_SCORE_MEDIUM),
            newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_ONLY, QUALITY_SINGLE_ZONE,
                    TELEPHONY_SCORE_HIGH),
            newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET, QUALITY_SINGLE_ZONE,
                    TELEPHONY_SCORE_HIGH),
            newTelephonyTestCase(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY,
                    QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, TELEPHONY_SCORE_HIGHEST),
            newTelephonyTestCase(MATCH_TYPE_EMULATOR_ZONE_ID, QUALITY_SINGLE_ZONE,
                    TELEPHONY_SCORE_HIGHEST),
    };

    private TimeZoneDetectorStrategyImpl mTimeZoneDetectorStrategy;
    private FakeTimeZoneDetectorStrategyCallback mFakeTimeZoneDetectorStrategyCallback;

    @Before
    public void setUp() {
        mFakeTimeZoneDetectorStrategyCallback = new FakeTimeZoneDetectorStrategyCallback();
        mTimeZoneDetectorStrategy =
                new TimeZoneDetectorStrategyImpl(mFakeTimeZoneDetectorStrategyCallback);
    }

    @Test
    public void testEmptyTelephonySuggestions() {
        TelephonyTimeZoneSuggestion slotIndex1TimeZoneSuggestion =
                createEmptySlotIndex1Suggestion();
        TelephonyTimeZoneSuggestion slotIndex2TimeZoneSuggestion =
                createEmptySlotIndex2Suggestion();
        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_TELEPHONY)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        script.suggestTelephonyTimeZone(slotIndex1TimeZoneSuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedTelephonyTimeZoneSuggestion expectedSlotIndex1ScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(slotIndex1TimeZoneSuggestion,
                        TELEPHONY_SCORE_NONE);
        assertEquals(expectedSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
        assertNull(mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
        assertEquals(expectedSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

        script.suggestTelephonyTimeZone(slotIndex2TimeZoneSuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedTelephonyTimeZoneSuggestion expectedSlotIndex2ScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(slotIndex2TimeZoneSuggestion,
                        TELEPHONY_SCORE_NONE);
        assertEquals(expectedSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
        assertEquals(expectedSlotIndex2ScoredSuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
        // SlotIndex1 should always beat slotIndex2, all other things being equal.
        assertEquals(expectedSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());
    }

    /**
     * Telephony suggestions have quality metadata. Ordinarily, low scoring suggestions are not
     * used, but this is not true if the device's time zone setting is uninitialized.
     */
    @Test
    public void testFirstPlausibleTelephonySuggestionAcceptedWhenTimeZoneUninitialized() {
        TelephonyTestCase testCase = newTelephonyTestCase(MATCH_TYPE_NETWORK_COUNTRY_ONLY,
                QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS, TELEPHONY_SCORE_LOW);
        TelephonyTimeZoneSuggestion lowQualitySuggestion =
                testCase.createSuggestion(SLOT_INDEX1, "America/New_York");

        // The device time zone setting is left uninitialized.
        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_TELEPHONY);

        // The very first suggestion will be taken.
        script.suggestTelephonyTimeZone(lowQualitySuggestion)
                .verifyTimeZoneSetAndReset(lowQualitySuggestion);

        // Assert internal service state.
        QualifiedTelephonyTimeZoneSuggestion expectedScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(
                        lowQualitySuggestion, testCase.expectedScore);
        assertEquals(expectedScoredSuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
        assertEquals(expectedScoredSuggestion,
                mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

        // Another low quality suggestion will be ignored now that the setting is initialized.
        TelephonyTimeZoneSuggestion lowQualitySuggestion2 =
                testCase.createSuggestion(SLOT_INDEX1, "America/Los_Angeles");
        script.suggestTelephonyTimeZone(lowQualitySuggestion2)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedTelephonyTimeZoneSuggestion expectedScoredSuggestion2 =
                new QualifiedTelephonyTimeZoneSuggestion(
                        lowQualitySuggestion2, testCase.expectedScore);
        assertEquals(expectedScoredSuggestion2,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
        assertEquals(expectedScoredSuggestion2,
                mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());
    }

    /**
     * Confirms that toggling the auto time zone detection setting has the expected behavior when
     * the strategy is "opinionated" when in telephony auto detection mode.
     */
    @Test
    public void testTogglingAutoTimeZoneDetection_autoModeTelephony() {
        Script script = new Script();

        for (TelephonyTestCase testCase : TELEPHONY_TEST_CASES) {
            // Start with the device in a known state.
            script.initializeAutoTimeZoneDetection(false, AUTO_MODE_TELEPHONY)
                    .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

            TelephonyTimeZoneSuggestion suggestion =
                    testCase.createSuggestion(SLOT_INDEX1, "Europe/London");
            script.suggestTelephonyTimeZone(suggestion);

            // When time zone detection is not enabled, the time zone suggestion will not be set
            // regardless of the score.
            script.verifyTimeZoneNotSet();

            // Assert internal service state.
            QualifiedTelephonyTimeZoneSuggestion expectedScoredSuggestion =
                    new QualifiedTelephonyTimeZoneSuggestion(suggestion, testCase.expectedScore);
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

            // Toggling the time zone setting on should cause the device setting to be set.
            script.autoTimeZoneDetectionEnabled(true);

            // When time zone detection is already enabled the suggestion (if it scores highly
            // enough) should be set immediately.
            if (testCase.expectedScore >= TELEPHONY_SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

            // Toggling the time zone setting should off should do nothing.
            script.autoTimeZoneDetectionEnabled(false)
                    .verifyTimeZoneNotSet();

            // Assert internal service state.
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());
        }
    }

    @Test
    public void testTelephonySuggestionsSingleSlotId() {
        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_TELEPHONY)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        for (TelephonyTestCase testCase : TELEPHONY_TEST_CASES) {
            makeSlotIndex1SuggestionAndCheckState(script, testCase);
        }

        /*
         * This is the same test as above but the test cases are in
         * reverse order of their expected score. New suggestions always replace previous ones:
         * there's effectively no history and so ordering shouldn't make any difference.
         */

        // Each test case will have the same or lower score than the last.
        ArrayList<TelephonyTestCase> descendingCasesByScore =
                new ArrayList<>(Arrays.asList(TELEPHONY_TEST_CASES));
        Collections.reverse(descendingCasesByScore);

        for (TelephonyTestCase testCase : descendingCasesByScore) {
            makeSlotIndex1SuggestionAndCheckState(script, testCase);
        }
    }

    private void makeSlotIndex1SuggestionAndCheckState(Script script, TelephonyTestCase testCase) {
        // Give the next suggestion a different zone from the currently set device time zone;
        String currentZoneId = mFakeTimeZoneDetectorStrategyCallback.getDeviceTimeZone();
        String suggestionZoneId =
                "Europe/London".equals(currentZoneId) ? "Europe/Paris" : "Europe/London";
        TelephonyTimeZoneSuggestion zoneSlotIndex1Suggestion =
                testCase.createSuggestion(SLOT_INDEX1, suggestionZoneId);
        QualifiedTelephonyTimeZoneSuggestion expectedZoneSlotIndex1ScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(
                        zoneSlotIndex1Suggestion, testCase.expectedScore);

        script.suggestTelephonyTimeZone(zoneSlotIndex1Suggestion);
        if (testCase.expectedScore >= TELEPHONY_SCORE_USAGE_THRESHOLD) {
            script.verifyTimeZoneSetAndReset(zoneSlotIndex1Suggestion);
        } else {
            script.verifyTimeZoneNotSet();
        }

        // Assert internal service state.
        assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
        assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());
    }

    /**
     * Tries a set of test cases to see if the slotIndex with the lowest numeric value is given
     * preference. This test also confirms that the time zone setting would only be set if a
     * suggestion is of sufficient quality.
     */
    @Test
    public void testMultipleSlotIndexSuggestionScoringAndSlotIndexBias() {
        String[] zoneIds = { "Europe/London", "Europe/Paris" };
        TelephonyTimeZoneSuggestion emptySlotIndex1Suggestion = createEmptySlotIndex1Suggestion();
        TelephonyTimeZoneSuggestion emptySlotIndex2Suggestion = createEmptySlotIndex2Suggestion();
        QualifiedTelephonyTimeZoneSuggestion expectedEmptySlotIndex1ScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(emptySlotIndex1Suggestion,
                        TELEPHONY_SCORE_NONE);
        QualifiedTelephonyTimeZoneSuggestion expectedEmptySlotIndex2ScoredSuggestion =
                new QualifiedTelephonyTimeZoneSuggestion(emptySlotIndex2Suggestion,
                        TELEPHONY_SCORE_NONE);

        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_TELEPHONY)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                // Initialize the latest suggestions as empty so we don't need to worry about nulls
                // below for the first loop.
                .suggestTelephonyTimeZone(emptySlotIndex1Suggestion)
                .suggestTelephonyTimeZone(emptySlotIndex2Suggestion)
                .resetState();

        for (TelephonyTestCase testCase : TELEPHONY_TEST_CASES) {
            TelephonyTimeZoneSuggestion zoneSlotIndex1Suggestion =
                    testCase.createSuggestion(SLOT_INDEX1, zoneIds[0]);
            TelephonyTimeZoneSuggestion zoneSlotIndex2Suggestion =
                    testCase.createSuggestion(SLOT_INDEX2, zoneIds[1]);
            QualifiedTelephonyTimeZoneSuggestion expectedZoneSlotIndex1ScoredSuggestion =
                    new QualifiedTelephonyTimeZoneSuggestion(zoneSlotIndex1Suggestion,
                            testCase.expectedScore);
            QualifiedTelephonyTimeZoneSuggestion expectedZoneSlotIndex2ScoredSuggestion =
                    new QualifiedTelephonyTimeZoneSuggestion(zoneSlotIndex2Suggestion,
                            testCase.expectedScore);

            // Start the test by making a suggestion for slotIndex1.
            script.suggestTelephonyTimeZone(zoneSlotIndex1Suggestion);
            if (testCase.expectedScore >= TELEPHONY_SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(zoneSlotIndex1Suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedEmptySlotIndex2ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
            assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

            // SlotIndex2 then makes an alternative suggestion with an identical score. SlotIndex1's
            // suggestion should still "win" if it is above the required threshold.
            script.suggestTelephonyTimeZone(zoneSlotIndex2Suggestion);
            script.verifyTimeZoneNotSet();

            // Assert internal service state.
            assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedZoneSlotIndex2ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
            // SlotIndex1 should always beat slotIndex2, all other things being equal.
            assertEquals(expectedZoneSlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

            // Withdrawing slotIndex1's suggestion should leave slotIndex2 as the new winner. Since
            // the zoneId is different, the time zone setting should be updated if the score is high
            // enough.
            script.suggestTelephonyTimeZone(emptySlotIndex1Suggestion);
            if (testCase.expectedScore >= TELEPHONY_SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(zoneSlotIndex2Suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedEmptySlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedZoneSlotIndex2ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
            assertEquals(expectedZoneSlotIndex2ScoredSuggestion,
                    mTimeZoneDetectorStrategy.findBestTelephonySuggestionForTests());

            // Reset the state for the next loop.
            script.suggestTelephonyTimeZone(emptySlotIndex2Suggestion)
                    .verifyTimeZoneNotSet();
            assertEquals(expectedEmptySlotIndex1ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1));
            assertEquals(expectedEmptySlotIndex2ScoredSuggestion,
                    mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX2));
        }
    }

    /**
     * The {@link TimeZoneDetectorStrategyImpl.Callback} is left to detect whether changing the time
     * zone is actually necessary. This test proves that the strategy doesn't assume it knows the
     * current setting.
     */
    @Test
    public void testTimeZoneDetectorStrategyDoesNotAssumeCurrentSetting_autoModeTelephony() {
        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_TELEPHONY);

        TelephonyTestCase testCase = newTelephonyTestCase(
                MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET, QUALITY_SINGLE_ZONE, TELEPHONY_SCORE_HIGH);
        TelephonyTimeZoneSuggestion losAngelesSuggestion =
                testCase.createSuggestion(SLOT_INDEX1, "America/Los_Angeles");
        TelephonyTimeZoneSuggestion newYorkSuggestion =
                testCase.createSuggestion(SLOT_INDEX1, "America/New_York");

        // Initialization.
        script.suggestTelephonyTimeZone(losAngelesSuggestion)
                .verifyTimeZoneSetAndReset(losAngelesSuggestion);
        // Suggest it again - it should not be set because it is already set.
        script.suggestTelephonyTimeZone(losAngelesSuggestion)
                .verifyTimeZoneNotSet();

        // Toggling time zone detection should set the device time zone only if the current setting
        // value is different from the most recent telephony suggestion.
        script.autoTimeZoneDetectionEnabled(false)
                .verifyTimeZoneNotSet()
                .autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneNotSet();

        // Simulate a user turning auto detection off, a new suggestion being made while auto
        // detection is off, and the user turning it on again.
        script.autoTimeZoneDetectionEnabled(false)
                .suggestTelephonyTimeZone(newYorkSuggestion)
                .verifyTimeZoneNotSet();
        // Latest suggestion should be used.
        script.autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(newYorkSuggestion);
    }

    @Test
    public void testManualSuggestion_autoTimeZoneDetectionEnabled_autoTelephonyMode() {
        checkManualSuggestion_autoTimeZoneDetectionEnabled(AUTO_MODE_TELEPHONY);
    }

    @Test
    public void testManualSuggestion_autoTimeZoneDetectionEnabled_autoGeolocationMode() {
        checkManualSuggestion_autoTimeZoneDetectionEnabled(AUTO_MODE_GEOLOCATION);
    }

    private void checkManualSuggestion_autoTimeZoneDetectionEnabled(int autoModeGeolocation) {
        Script script = new Script()
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .initializeAutoTimeZoneDetection(true, autoModeGeolocation);

        // Auto time zone detection is enabled so the manual suggestion should be ignored.
        script.suggestManualTimeZone(createManualSuggestion("Europe/Paris"))
                .verifyTimeZoneNotSet();
    }

    @Test
    public void testManualSuggestion_autoTimeZoneDetectionDisabled() {
        Script script = new Script()
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .initializeAutoTimeZoneDetection(false, AUTO_MODE_TELEPHONY);

        // Auto time zone detection is disabled so the manual suggestion should be used.
        ManualTimeZoneSuggestion manualSuggestion = createManualSuggestion("Europe/Paris");
        script.suggestManualTimeZone(manualSuggestion)
            .verifyTimeZoneSetAndReset(manualSuggestion);
    }

    @Test
    public void testEmptyGeolocationSuggestion() {
        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_GEOLOCATION)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        GeolocationTimeZoneSuggestion emptySuggestion = createEmptyGeoLocationSuggestion();

        script.suggestGeolocationTimeZone(emptySuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        assertEquals(emptySuggestion, mTimeZoneDetectorStrategy.getLatestGeolocationSuggestion());
    }

    /** A basic test that confirms that geolocation suggestions are used. */
    @Test
    public void testGeolocationSuggestion() {
        GeolocationTimeZoneSuggestion suggestion = createGeoLocationSuggestion("Europe/London");

        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_GEOLOCATION)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        script.suggestGeolocationTimeZone(suggestion)
                .verifyTimeZoneSetAndReset(suggestion);

        // Assert internal service state.
        assertEquals(suggestion, mTimeZoneDetectorStrategy.getLatestGeolocationSuggestion());
    }

    /**
     * Confirms that toggling the auto time zone detection enabled setting has the expected behavior
     * when the strategy is "opinionated" and "un-opinionated" when in geolocation detection mode.
     */
    @Test
    public void testTogglingAutoTimeZoneDetectionEnabled_autoModeGeolocation() {
        GeolocationTimeZoneSuggestion geolocationSuggestion =
                createGeoLocationSuggestion("Europe/London");
        GeolocationTimeZoneSuggestion emptyGeolocationSuggestion =
                createEmptyGeoLocationSuggestion();
        ManualTimeZoneSuggestion manualSuggestion = createManualSuggestion("Europe/Paris");

        Script script = new Script()
                .initializeAutoTimeZoneDetection(false, AUTO_MODE_GEOLOCATION)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        script.suggestGeolocationTimeZone(geolocationSuggestion);

        // When time zone detection is not enabled, the time zone suggestion will not be set.
        script.verifyTimeZoneNotSet();

        // Assert internal service state.
        assertEquals(geolocationSuggestion,
                mTimeZoneDetectorStrategy.getLatestGeolocationSuggestion());

        // Toggling the time zone setting on should cause the device setting to be set.
        script.autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(geolocationSuggestion);

        // Toggling the time zone setting should off should do nothing because the device is now
        // set to that time zone.
        script.autoTimeZoneDetectionEnabled(false)
                .verifyTimeZoneNotSet()
                .autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneNotSet();

        // Now toggle auto time zone setting, and confirm it is opinionated.
        script.autoTimeZoneDetectionEnabled(false)
                .suggestManualTimeZone(manualSuggestion)
                .verifyTimeZoneSetAndReset(manualSuggestion)
                .autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(geolocationSuggestion);

        // Now withdraw the geolocation suggestion, and assert the strategy is no longer
        // opinionated.
        script.suggestGeolocationTimeZone(emptyGeolocationSuggestion)
                .verifyTimeZoneNotSet()
                .autoTimeZoneDetectionEnabled(false)
                .verifyTimeZoneNotSet()
                .suggestManualTimeZone(manualSuggestion)
                .verifyTimeZoneSetAndReset(manualSuggestion)
                .autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        assertEquals(emptyGeolocationSuggestion,
                mTimeZoneDetectorStrategy.getLatestGeolocationSuggestion());
    }

    /**
     * Confirms that toggling the auto time zone detection mode has the expected behavior.
     */
    @Test
    public void testTogglingAutoTimeZoneDetectionMode() {
        GeolocationTimeZoneSuggestion geolocationSuggestion =
                createGeoLocationSuggestion("Europe/London");
        TelephonyTimeZoneSuggestion telephonySuggestion = createTelephonySuggestion(
                SLOT_INDEX1, MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET, QUALITY_SINGLE_ZONE,
                "Europe/Paris");

        Script script = new Script()
                .initializeAutoTimeZoneDetection(false, AUTO_MODE_TELEPHONY)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        // Add suggestions. Nothing should happen as time zone detection is disabled.
        script.suggestGeolocationTimeZone(geolocationSuggestion)
                .verifyTimeZoneNotSet();
        script.suggestTelephonyTimeZone(telephonySuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        assertEquals(geolocationSuggestion,
                mTimeZoneDetectorStrategy.getLatestGeolocationSuggestion());
        assertEquals(telephonySuggestion,
                mTimeZoneDetectorStrategy.getLatestTelephonySuggestion(SLOT_INDEX1).suggestion);

        // Toggling the time zone setting on should cause the device setting to be set from the
        // telephony signal, as we've started in telephony mode.
        script.autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(telephonySuggestion);

        // Toggling the mode should cause the device setting to change.
        script.autoTimeZoneDetectionModeSet(AUTO_MODE_GEOLOCATION)
                .verifyTimeZoneSetAndReset(geolocationSuggestion);

        script.autoTimeZoneDetectionModeSet(AUTO_MODE_TELEPHONY)
                .verifyTimeZoneSetAndReset(telephonySuggestion);
    }

    /**
     * The {@link TimeZoneDetectorStrategyImpl.Callback} is left to detect whether changing the time
     * zone is actually necessary. This test proves that the strategy doesn't assume it knows the
     * current setting.
     */
    @Test
    public void testTimeZoneDetectorStrategyDoesNotAssumeCurrentSetting_autoModeGeolocation() {
        GeolocationTimeZoneSuggestion losAngelesSuggestion =
                createGeoLocationSuggestion("America/Los_Angeles");
        GeolocationTimeZoneSuggestion newYorkSuggestion =
                createGeoLocationSuggestion("America/New_York");

        Script script = new Script()
                .initializeAutoTimeZoneDetection(true, AUTO_MODE_GEOLOCATION);

        // Initialization.
        script.suggestGeolocationTimeZone(losAngelesSuggestion)
                .verifyTimeZoneSetAndReset(losAngelesSuggestion);
        // Suggest it again - it should not be set because it is already set.
        script.suggestGeolocationTimeZone(losAngelesSuggestion)
                .verifyTimeZoneNotSet();

        // Toggling time zone detection should set the device time zone only if the current setting
        // value is different from the most recent telephony suggestion.
        script.autoTimeZoneDetectionEnabled(false)
                .verifyTimeZoneNotSet()
                .autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneNotSet();

        // Simulate a user turning auto detection off, a new suggestion being made while auto
        // detection is off, and the user turning it on again.
        script.autoTimeZoneDetectionEnabled(false)
                .suggestGeolocationTimeZone(newYorkSuggestion)
                .verifyTimeZoneNotSet();
        // Latest suggestion should be used.
        script.autoTimeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(newYorkSuggestion);
    }

    private ManualTimeZoneSuggestion createManualSuggestion(String zoneId) {
        return new ManualTimeZoneSuggestion(zoneId);
    }

    private static TelephonyTimeZoneSuggestion createTelephonySuggestion(
            int slotIndex, @MatchType int matchType, @Quality int quality, String zoneId) {
        return new TelephonyTimeZoneSuggestion.Builder(slotIndex)
                .setMatchType(matchType)
                .setQuality(quality)
                .setZoneId(zoneId)
                .build();
    }

    private static TelephonyTimeZoneSuggestion createEmptySlotIndex1Suggestion() {
        return new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX1).build();
    }

    private static TelephonyTimeZoneSuggestion createEmptySlotIndex2Suggestion() {
        return new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX2).build();
    }

    private static GeolocationTimeZoneSuggestion createGeoLocationSuggestion(String zoneId) {
        return new GeolocationTimeZoneSuggestion(zoneId);
    }

    private static GeolocationTimeZoneSuggestion createEmptyGeoLocationSuggestion() {
        return createGeoLocationSuggestion(null);
    }

    static class FakeTimeZoneDetectorStrategyCallback
            implements TimeZoneDetectorStrategyImpl.Callback {

        private boolean mAutoTimeZoneDetectionEnabled;
        private int mAutoTimeZoneDetectionMode;
        private TestState<String> mTimeZoneId = new TestState<>();

        @Override
        public boolean isAutoTimeZoneDetectionEnabled() {
            return mAutoTimeZoneDetectionEnabled;
        }

        @Override
        @AutoMode
        public int getAutoTimeZoneDetectionMode() {
            return mAutoTimeZoneDetectionMode;
        }

        @Override
        public boolean isDeviceTimeZoneInitialized() {
            return mTimeZoneId.getLatest() != null;
        }

        @Override
        public String getDeviceTimeZone() {
            return mTimeZoneId.getLatest();
        }

        @Override
        public void setDeviceTimeZone(String zoneId) {
            mTimeZoneId.set(zoneId);
        }

        void initializeAutoTimeZoneDetection(boolean enabled, @AutoMode int autoDetectionMode) {
            mAutoTimeZoneDetectionEnabled = enabled;
            mAutoTimeZoneDetectionMode = autoDetectionMode;
        }

        void initializeTimeZone(String zoneId) {
            mTimeZoneId.init(zoneId);
        }

        void setAutoTimeZoneDetectionEnabled(boolean enabled) {
            mAutoTimeZoneDetectionEnabled = enabled;
        }

        void setAutoTimeZoneDetectionMode(int autoDetectionMode) {
            mAutoTimeZoneDetectionMode = autoDetectionMode;
        }

        void assertTimeZoneNotSet() {
            mTimeZoneId.assertHasNotBeenSet();
        }

        void assertTimeZoneSet(String timeZoneId) {
            mTimeZoneId.assertHasBeenSet();
            mTimeZoneId.assertChangeCount(1);
            mTimeZoneId.assertLatestEquals(timeZoneId);
        }

        void commitAllChanges() {
            mTimeZoneId.commitLatest();
        }
    }

    /** Some piece of state that tests want to track. */
    private static class TestState<T> {
        private T mInitialValue;
        private LinkedList<T> mValues = new LinkedList<>();

        void init(T value) {
            mValues.clear();
            mInitialValue = value;
        }

        void set(T value) {
            mValues.addFirst(value);
        }

        boolean hasBeenSet() {
            return mValues.size() > 0;
        }

        void assertHasNotBeenSet() {
            assertFalse(hasBeenSet());
        }

        void assertHasBeenSet() {
            assertTrue(hasBeenSet());
        }

        void commitLatest() {
            if (hasBeenSet()) {
                mInitialValue = mValues.getLast();
                mValues.clear();
            }
        }

        void assertLatestEquals(T expected) {
            assertEquals(expected, getLatest());
        }

        void assertChangeCount(int expectedCount) {
            assertEquals(expectedCount, mValues.size());
        }

        public T getLatest() {
            if (hasBeenSet()) {
                return mValues.getFirst();
            }
            return mInitialValue;
        }
    }

    /**
     * A "fluent" class allows reuse of code in tests: initialization, simulation and verification
     * logic.
     */
    private class Script {

        Script initializeAutoTimeZoneDetection(boolean enabled, @AutoMode int autoDetectionMode) {
            mFakeTimeZoneDetectorStrategyCallback.initializeAutoTimeZoneDetection(
                    enabled, autoDetectionMode);
            return this;
        }

        Script initializeTimeZoneSetting(String zoneId) {
            mFakeTimeZoneDetectorStrategyCallback.initializeTimeZone(zoneId);
            return this;
        }

        Script autoTimeZoneDetectionEnabled(boolean enabled) {
            mFakeTimeZoneDetectorStrategyCallback.setAutoTimeZoneDetectionEnabled(enabled);
            mTimeZoneDetectorStrategy.handleAutoTimeZoneDetectionChanged();
            return this;
        }

        Script autoTimeZoneDetectionModeSet(@AutoMode int autoDetectionMode) {
            mFakeTimeZoneDetectorStrategyCallback.setAutoTimeZoneDetectionMode(autoDetectionMode);
            mTimeZoneDetectorStrategy.handleAutoTimeZoneDetectionChanged();
            return this;
        }

        /** Simulates the time zone detection strategy receiving a user-originated suggestion. */
        Script suggestManualTimeZone(ManualTimeZoneSuggestion manualTimeZoneSuggestion) {
            mTimeZoneDetectorStrategy.suggestManualTimeZone(manualTimeZoneSuggestion);
            return this;
        }

        /**
         * Simulates the time zone detection strategy receiving a geolocation-originated
         * suggestion.
         */
        Script suggestGeolocationTimeZone(GeolocationTimeZoneSuggestion suggestion) {
            mTimeZoneDetectorStrategy.suggestGeolocationTimeZone(suggestion);
            return this;
        }

        /**
         * Simulates the time zone detection strategy receiving a telephony-originated suggestion.
         */
        Script suggestTelephonyTimeZone(TelephonyTimeZoneSuggestion timeZoneSuggestion) {
            mTimeZoneDetectorStrategy.suggestTelephonyTimeZone(timeZoneSuggestion);
            return this;
        }

        /**
         * Confirms that the device's time zone has not been set by previous actions since the test
         * state was last reset.
         */
        Script verifyTimeZoneNotSet() {
            mFakeTimeZoneDetectorStrategyCallback.assertTimeZoneNotSet();
            return this;
        }

        /** Verifies the device's time zone has been set and clears change tracking history. */
        Script verifyTimeZoneSetAndReset(String zoneId) {
            mFakeTimeZoneDetectorStrategyCallback.assertTimeZoneSet(zoneId);
            mFakeTimeZoneDetectorStrategyCallback.commitAllChanges();
            return this;
        }

        Script verifyTimeZoneSetAndReset(TelephonyTimeZoneSuggestion suggestion) {
            return verifyTimeZoneSetAndReset(suggestion.getZoneId());
        }

        Script verifyTimeZoneSetAndReset(ManualTimeZoneSuggestion suggestion) {
            return verifyTimeZoneSetAndReset(suggestion.getZoneId());
        }

        Script verifyTimeZoneSetAndReset(GeolocationTimeZoneSuggestion suggestion) {
            return verifyTimeZoneSetAndReset(suggestion.getZoneId());
        }

        Script resetState() {
            mFakeTimeZoneDetectorStrategyCallback.commitAllChanges();
            return this;
        }
    }

    private static class TelephonyTestCase {
        public final int matchType;
        public final int quality;
        public final int expectedScore;

        TelephonyTestCase(int matchType, int quality, int expectedScore) {
            this.matchType = matchType;
            this.quality = quality;
            this.expectedScore = expectedScore;
        }

        private TelephonyTimeZoneSuggestion createSuggestion(int slotIndex, String zoneId) {
            return new TelephonyTimeZoneSuggestion.Builder(slotIndex)
                    .setZoneId(zoneId)
                    .setMatchType(matchType)
                    .setQuality(quality)
                    .build();
        }
    }

    private static TelephonyTestCase newTelephonyTestCase(
            @MatchType int matchType, @Quality int quality, int expectedScore) {
        return new TelephonyTestCase(matchType, quality, expectedScore);
    }
}
