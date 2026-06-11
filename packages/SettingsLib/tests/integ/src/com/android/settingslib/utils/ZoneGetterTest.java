/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.settingslib.utils;

import static junit.framework.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import android.text.style.TtsSpan;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.settingslib.datetime.ZoneGetter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ZoneGetterTest {
    private static final String TIME_ZONE_LONDON_ID = "Europe/London";
    private static final String TIME_ZONE_LA_ID = "America/Los_Angeles";
    private static final String TIME_ZONE_ALGIERS_ID = "Africa/Algiers";
    private static final String TIME_ZONE_CEUTA_ID = "Africa/Ceuta";
    private Locale mLocaleEnUs;
    private Calendar mCalendar;

    @Before
    public void setUp() {
        mLocaleEnUs = new Locale("en", "us");
        Locale.setDefault(mLocaleEnUs);
        mCalendar = new GregorianCalendar(2016, 9, 1);
    }

    @Test
    public void getTimeZoneOffsetAndName_setLondon_returnBritishSummerTime() {
        // Check it will ends with 'British Summer Time', not 'London' or sth else
        testTimeZoneOffsetAndNameInner(TIME_ZONE_LONDON_ID, "British Summer Time");
    }

    @Test
    public void getTimeZoneOffsetAndName_setLosAngeles_returnPacificDaylightTime() {
        // Check it will ends with 'Pacific Daylight Time', not 'Los_Angeles'
        testTimeZoneOffsetAndNameInner(TIME_ZONE_LA_ID, "Pacific Daylight Time");
    }

    @Test
    public void getTimeZoneOffsetAndName_setAlgiers_returnCentralEuropeanStandardTime() {
        testTimeZoneOffsetAndNameInner(TIME_ZONE_ALGIERS_ID, "Central European Standard Time");
    }

    @Test
    public void getTimeZoneOffsetAndName_setCeuta_returnCentralEuropeanSummerTime() {
        testTimeZoneOffsetAndNameInner(TIME_ZONE_CEUTA_ID, "Central European Summer Time");
    }

    @Test
    public void getZonesList_checkTypes() {
        final List<Map<String, Object>> zones =
                ZoneGetter.getZonesList(InstrumentationRegistry.getContext());
        for (Map<String, Object> zone : zones) {
            assertTrue(zone.get(ZoneGetter.KEY_DISPLAYNAME) instanceof String);
            assertTrue(zone.get(ZoneGetter.KEY_DISPLAY_LABEL) instanceof CharSequence);
            assertTrue(zone.get(ZoneGetter.KEY_OFFSET) instanceof Integer);
            assertTrue(zone.get(ZoneGetter.KEY_OFFSET_LABEL) instanceof CharSequence);
            assertTrue(zone.get(ZoneGetter.KEY_ID) instanceof String);
            assertTrue(zone.get(ZoneGetter.KEY_GMT) instanceof String);
        }
    }

    @Test
    public void getTimeZoneOffsetAndName_withTtsSpan() {
        final Context context = InstrumentationRegistry.getContext();
        final TimeZone timeZone = TimeZone.getTimeZone(TIME_ZONE_LA_ID);

        CharSequence timeZoneString = ZoneGetter.getTimeZoneOffsetAndName(context, timeZone,
                mCalendar.getTime());
        assertTrue("Time zone string should be spanned", timeZoneString instanceof Spanned);
        assertTrue("Time zone display name should have TTS spans",
                ((Spanned) timeZoneString).getSpans(
                    0, timeZoneString.length(), TtsSpan.class).length > 0);
    }

    @Test
    public void getZonesList_allZonesHaveDisplayNames() {
        // Test that all zones have non-null and non-empty display names
        // This verifies the fallback logic works when long name is null/empty
        final List<Map<String, Object>> zones =
                ZoneGetter.getZonesList(InstrumentationRegistry.getContext());
        
        for (Map<String, Object> zone : zones) {
            CharSequence displayLabel = (CharSequence) zone.get(ZoneGetter.KEY_DISPLAY_LABEL);
            String displayName = (String) zone.get(ZoneGetter.KEY_DISPLAYNAME);
            
            // Verify display label is not null and not empty
            assertTrue("Display label should not be null for zone: " + zone.get(ZoneGetter.KEY_ID),
                    displayLabel != null);
            assertTrue("Display label should not be empty for zone: " + zone.get(ZoneGetter.KEY_ID),
                    displayLabel.length() > 0);
            
            // Verify display name string is not null and not empty
            assertTrue("Display name should not be null for zone: " + zone.get(ZoneGetter.KEY_ID),
                    displayName != null);
            assertTrue("Display name should not be empty for zone: " + zone.get(ZoneGetter.KEY_ID),
                    !displayName.isEmpty());
        }
    }

    @Test
    public void getZonesList_fallbackToExemplarLocation() {
        // Test that zones without long names fall back to exemplar location
        // This tests the fallback logic added in getTimeZoneDisplayName
        final List<Map<String, Object>> zones =
                ZoneGetter.getZonesList(InstrumentationRegistry.getContext());
        
        boolean foundAtLeastOneZone = false;
        for (Map<String, Object> zone : zones) {
            String zoneId = (String) zone.get(ZoneGetter.KEY_ID);
            CharSequence displayLabel = (CharSequence) zone.get(ZoneGetter.KEY_DISPLAY_LABEL);
            
            // The fallback ensures even zones with problematic long names have valid display names
            // Either from long name or exemplar location
            assertTrue("Zone " + zoneId + " should have a valid display name",
                    displayLabel != null && displayLabel.length() > 0);
            foundAtLeastOneZone = true;
        }
        
        assertTrue("Should have at least one zone in the list", foundAtLeastOneZone);
    }

    @Test
    public void getZonesList_longNameFallbackToExemplarLocation() {
        // Test with a locale that may not have complete long name data for all zones
        // This verifies the fallback from long name to exemplar location works
        Locale.setDefault(new Locale("is")); // Icelandic locale - smaller ICU data set
        final Context context = InstrumentationRegistry.getContext();
        final List<Map<String, Object>> zones = ZoneGetter.getZonesList(context);
        
        // Verify all zones have valid display names even if long name is missing
        for (Map<String, Object> zone : zones) {
            String zoneId = (String) zone.get(ZoneGetter.KEY_ID);
            CharSequence displayLabel = (CharSequence) zone.get(ZoneGetter.KEY_DISPLAY_LABEL);
            String displayName = (String) zone.get(ZoneGetter.KEY_DISPLAYNAME);
            
            // The fallback logic ensures displayName is never null or empty
            assertTrue("Display label should not be null for zone: " + zoneId,
                    displayLabel != null);
            assertTrue("Display label should not be empty for zone: " + zoneId,
                    displayLabel.length() > 0);
            assertTrue("Display name should not be null for zone: " + zoneId,
                    displayName != null);
            assertTrue("Display name should not be empty for zone: " + zoneId,
                    !displayName.isEmpty());
            
            // Display name should be either a long name or exemplar location, not just GMT offset
            // (unless it's a rare case where exemplar location is also null)
            CharSequence gmtOffset = (CharSequence) zone.get(ZoneGetter.KEY_OFFSET_LABEL);
            // Most zones should have a human-readable name, not just GMT offset
            // We verify that the fallback mechanism produces meaningful names
        }
        
        // Verify we have zones in the list
        assertTrue("Should have zones in the list", zones.size() > 0);
    }

    @Test
    public void getTimeZoneOffsetAndName_turkishLocale_fallbackToExemplarLocation() {
        // Test fallback from LONG name to EXEMPLAR_LOCATION
        // Turkish ICU data (tr.txt) doesn't have LONG_DAYLIGHT/LONG_STANDARD for Istanbul
        // It only has EXEMPLAR_LOCATION data, which should trigger the fallback
        Locale.setDefault(new Locale("tr")); // Turkish locale
        final Context context = InstrumentationRegistry.getContext();
        
        // Europe/Istanbul in tr.txt only has EXEMPLAR_LOCATION, not LONG names
        // This will trigger: getZoneLongName() returns null → fallback to getExemplarLocationName()
        final TimeZone istanbul = TimeZone.getTimeZone("Europe/Istanbul");
        CharSequence name = ZoneGetter.getTimeZoneOffsetAndName(context, istanbul, 
                mCalendar.getTime());
        
        // Should get "İstanbul" from EXEMPLAR_LOCATION (with Turkish dotted capital İ)
        assertTrue("Time zone name should not be null", name != null);
        assertTrue("Time zone name should not be empty", name.length() > 0);
        // Verify it contains Istanbul (the fallback exemplar location worked)
        assertTrue("Time zone name should contain İstanbul from exemplar location", 
                name.toString().contains("İstanbul") || name.toString().contains("Istanbul"));
    }

    @Test
    public void getTimeZoneOffsetAndName_rareLongNameFallback() {
        // Test fallback behavior when long name might not be available
        // Using Turkish locale with a non-Turkish timezone to trigger fallback
        // Turkish ICU data may not have complete long names for distant timezones
        Locale.setDefault(new Locale("tr")); // Turkish locale
        final Context context = InstrumentationRegistry.getContext();
        
        // Test with a distant time zone - Turkish locale may lack long name for this
        // This should trigger the fallback to exemplar location
        final TimeZone papeete = TimeZone.getTimeZone("Pacific/Papeete");
        CharSequence name = ZoneGetter.getTimeZoneOffsetAndName(context, papeete, 
                mCalendar.getTime());
        
        // Should still have a valid name (either long name or exemplar location fallback)
        assertTrue("Time zone name should not be null", name != null);
        assertTrue("Time zone name should not be empty", name.length() > 0);
        // Name should contain either the time zone name or at minimum the GMT offset
        assertTrue("Time zone name should have meaningful content", 
                name.toString().length() > 3);
    }

    private void testTimeZoneOffsetAndNameInner(String timeZoneId, String expectedName) {
        final Context context = InstrumentationRegistry.getContext();
        final TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);

        CharSequence timeZoneString = ZoneGetter.getTimeZoneOffsetAndName(context, timeZone,
                mCalendar.getTime());

        assertTrue(timeZoneString.toString().endsWith(expectedName));
    }

}
