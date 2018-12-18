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

package com.android.server.net.ipmemorystore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RelevanceUtils}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RelevanceUtilsTests {
    @Test
    public void testComputeRelevanceForTargetDate() {

    }

    @Test
    public void testIncreaseRelevance() {
        long expiry = System.currentTimeMillis();

        final long firstBump = RelevanceUtils.bumpExpiryDate(expiry);
        // Though a few milliseconds might have elapsed, the first bump should push the duration
        // to days in the future, so unless this test takes literal days between these two lines,
        // this should always pass.
        assertTrue(firstBump > expiry);

        expiry = 0;
        long lastDifference = Long.MAX_VALUE;
        // The relevance should be capped in at most this many steps. Otherwise, fail.
        final int steps = 1000;
        for (int i = 0; i < steps; ++i) {
            final long newExpiry = RelevanceUtils.bumpExpiryDuration(expiry);
            if (newExpiry == expiry) {
                // The relevance should be capped. Make sure it is, then exit without failure.
                assertEquals(newExpiry, RelevanceUtils.CAPPED_RELEVANCE_LIFETIME_MS);
                return;
            }
            // Make sure the new expiry is further in the future than last time.
            assertTrue(newExpiry > expiry);
            // Also check that it was not bumped as much as the last bump, because the
            // decay must be exponential.
            assertTrue(newExpiry - expiry < lastDifference);
            lastDifference = newExpiry - expiry;
            expiry = newExpiry;
        }
        fail("Relevance failed to go to the maximum value after " + steps + " bumps");
    }

    @Test
    public void testContinuity() {
        final long expiry = System.currentTimeMillis();

        // Relevance at expiry and after expiry should be the cap.
        final int relevanceBeforeMaxLifetime = RelevanceUtils.computeRelevanceForTargetDate(expiry,
                expiry - (RelevanceUtils.CAPPED_RELEVANCE_LIFETIME_MS + 1_000_000));
        assertEquals(relevanceBeforeMaxLifetime, RelevanceUtils.CAPPED_RELEVANCE);
        final int relevanceForMaxLifetime = RelevanceUtils.computeRelevanceForTargetDate(expiry,
                expiry - RelevanceUtils.CAPPED_RELEVANCE_LIFETIME_MS);
        assertEquals(relevanceForMaxLifetime, RelevanceUtils.CAPPED_RELEVANCE);

        // If the max relevance is reached at the cap lifetime, one millisecond less than this
        // should be very close. Strictly speaking this is a bit brittle, but it should be
        // good enough for the purposes of the memory store.
        final int relevanceForOneMillisecLessThanCap = RelevanceUtils.computeRelevanceForTargetDate(
                expiry, expiry - RelevanceUtils.CAPPED_RELEVANCE_LIFETIME_MS + 1);
        assertTrue(relevanceForOneMillisecLessThanCap <= RelevanceUtils.CAPPED_RELEVANCE);
        assertTrue(relevanceForOneMillisecLessThanCap >= RelevanceUtils.CAPPED_RELEVANCE - 10);

        // Likewise the relevance one millisecond before expiry should be very close to 0. It's
        // fine if it rounds down to 0.
        final int relevanceOneMillisecBeforeExpiry = RelevanceUtils.computeRelevanceForTargetDate(
                expiry, expiry - 1);
        assertTrue(relevanceOneMillisecBeforeExpiry <= 10);
        assertTrue(relevanceOneMillisecBeforeExpiry >= 0);

        final int relevanceAtExpiry = RelevanceUtils.computeRelevanceForTargetDate(expiry, expiry);
        assertEquals(relevanceAtExpiry, 0);
        final int relevanceAfterExpiry = RelevanceUtils.computeRelevanceForTargetDate(expiry,
                expiry + 1_000_000);
        assertEquals(relevanceAfterExpiry, 0);
    }

    // testIncreaseRelevance makes sure bumping the expiry continuously always yields a
    // monotonically increasing date as a side effect, but this tests that the relevance (as
    // opposed to the expiry date) increases monotonically with increasing periods.
    @Test
    public void testMonotonicity() {
        // Hopefully the relevance is granular enough to give a different value for every one
        // of this number of steps.
        final int steps = 40;
        final long expiry = System.currentTimeMillis();

        int lastRelevance = -1;
        for (int i = 0; i < steps; ++i) {
            final long date = expiry - i * (RelevanceUtils.CAPPED_RELEVANCE_LIFETIME_MS / steps);
            final int relevance = RelevanceUtils.computeRelevanceForTargetDate(expiry, date);
            assertTrue(relevance > lastRelevance);
            lastRelevance = relevance;
        }
    }
}
