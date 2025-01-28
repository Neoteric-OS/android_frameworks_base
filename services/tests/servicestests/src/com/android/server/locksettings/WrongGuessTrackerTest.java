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

package com.android.server.locksettings;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/**
 * atest FrameworksServicesTests:WrongGuessTrackerTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class WrongGuessTrackerTest {

    private final TestInjector mInjector = new TestInjector();
    private final WrongGuessTracker mTracker = new WrongGuessTracker(mInjector);
    private static final int MAX_LENGTH = WrongGuessTracker.MAX_LENGTH;
    private static final int TIMEOUT_MILLIS = WrongGuessTracker.TIMEOUT_MILLIS;

    @Test
    public void testWrongGuessIsRemembered() {
        final int userId = 0;
        final LockscreenCredential guess = newPassword("password");

        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
        mTracker.insert(userId, guess);
        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
    }

    @Test
    public void testClear_clearsSingleGuess() {
        final int userId = 0;
        final LockscreenCredential guess = newPassword("password");

        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
        mTracker.insert(userId, guess);
        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
        mTracker.clear(userId);
        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
    }

    @Test
    public void testClear_clearsAllGuesses() {
        final int userId = 0;

        for (int i = 0; i < MAX_LENGTH; i++) {
            mTracker.insert(userId, newPassword("password" + i));
        }
        mTracker.clear(userId);
        for (int i = 0; i < MAX_LENGTH; i++) {
            assertThat(mTracker.checkIfSeen(userId, newPassword("password" + i))).isFalse();
        }
    }

    @Test
    public void testTrackerSavesOwnCopyOfCredential() {
        final int userId = 0;
        final LockscreenCredential guess = newPassword("password");

        mTracker.insert(userId, guess);
        guess.zeroize(); // Should have no effect on tracker.
        assertThat(mTracker.checkIfSeen(userId, newPassword("password"))).isTrue();
    }

    @Test
    public void testOnlyMostRecentGuessesAreRemembered() {
        final int userId = 0;
        final Random r = new Random(/* seed= */ 0);
        final int numIterations = 10;

        for (int i = 0; i < numIterations; i++) {
            final ArrayList<Integer> guessHistory = new ArrayList<>();

            mTracker.clear(userId);

            // Add 3*MAX_LENGTH guesses, randomly chosen from 1.5*MAX_LENGTH unique passwords.
            final int numAttempts = MAX_LENGTH * 3;
            final int numUniquePasswords = MAX_LENGTH * 3 / 2;
            for (int j = 0; j < numAttempts; j++) {
                final int passwordNum = r.nextInt() % numUniquePasswords;
                final LockscreenCredential guess = newPassword("password" + passwordNum);

                if (!mTracker.checkIfSeen(userId, guess)) {
                    mTracker.insert(userId, guess);
                }
                guessHistory.add(passwordNum);
            }

            // Verify that guesses in the last MAX_LENGTH unique guesses are remembered, and that
            // guesses not in the last MAX_LENGTH unique guesses have been forgotten.
            final HashSet<Integer> recentUniqueGuesses = new HashSet<>();
            for (int j = numAttempts - 1; j >= 0; j--) {
                if (recentUniqueGuesses.size() < MAX_LENGTH) {
                    recentUniqueGuesses.add(guessHistory.get(j));
                }
            }
            for (int j = 0; j < numUniquePasswords; j++) {
                if (recentUniqueGuesses.contains(j)) {
                    assertThat(mTracker.checkIfSeen(userId, newPassword("password" + j))).isTrue();
                } else {
                    assertThat(mTracker.checkIfSeen(userId, newPassword("password" + j))).isFalse();
                }
            }
        }
    }

    @Test
    public void testDifferentUsersAreTrackedSeparately() {
        final int userId1 = 0;
        final int userId2 = 10;
        final LockscreenCredential guess = newPassword("password");

        mTracker.insert(userId1, guess);
        assertThat(mTracker.checkIfSeen(userId1, guess)).isTrue();
        assertThat(mTracker.checkIfSeen(userId2, guess)).isFalse();

        mTracker.insert(userId2, guess);
        assertThat(mTracker.checkIfSeen(userId1, guess)).isTrue();
        assertThat(mTracker.checkIfSeen(userId2, guess)).isTrue();

        mTracker.clear(userId1);
        assertThat(mTracker.checkIfSeen(userId1, guess)).isFalse();
        assertThat(mTracker.checkIfSeen(userId2, guess)).isTrue();

        mTracker.clear(userId2);
        assertThat(mTracker.checkIfSeen(userId1, guess)).isFalse();
        assertThat(mTracker.checkIfSeen(userId2, guess)).isFalse();
    }

    @Test
    public void testWrongGuessesForgottenAfterTimeout() {
        final int userId = 0;
        final LockscreenCredential guess = newPassword("password");

        mTracker.insert(userId, guess);

        assertThat(mInjector.clearWorkList).hasSize(1);
        ClearWork work = mInjector.clearWorkList.get(0);
        assertThat(work.delayMillis).isEqualTo(TIMEOUT_MILLIS);

        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
        work.runnable.run();
        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
    }

    @Test
    public void testExplicitClearCancelsClearWork() {
        final int userId = 0;
        final LockscreenCredential guess = newPassword("password");

        mTracker.insert(userId, guess);
        assertThat(mInjector.clearWorkList).hasSize(1);
        mTracker.clear(userId);
        assertThat(mInjector.clearWorkList).isEmpty();
    }

    @Test
    public void testPin() {
        final int userId = 0;
        final LockscreenCredential guess = newPin("1234");

        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
        mTracker.insert(userId, guess);
        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
        mTracker.clear(userId);
        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
    }

    @Test
    public void testPattern() {
        final int userId = 0;
        final LockscreenCredential guess = newPattern("1234");

        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
        mTracker.insert(userId, guess);
        assertThat(mTracker.checkIfSeen(userId, guess)).isTrue();
        mTracker.clear(userId);
        assertThat(mTracker.checkIfSeen(userId, guess)).isFalse();
    }

    @Test
    public void testDifferentCredentialTypesAreConsideredDifferent() {
        final int userId = 0;
        final LockscreenCredential password = newPassword("1234");
        final LockscreenCredential pin = newPin("1234");
        final LockscreenCredential pattern = newPattern("1234");

        mTracker.insert(userId, password);
        assertThat(mTracker.checkIfSeen(userId, password)).isTrue();
        assertThat(mTracker.checkIfSeen(userId, pin)).isFalse();
        assertThat(mTracker.checkIfSeen(userId, pattern)).isFalse();

        mTracker.clear(userId);
        mTracker.insert(userId, pin);
        assertThat(mTracker.checkIfSeen(userId, password)).isFalse();
        assertThat(mTracker.checkIfSeen(userId, pin)).isTrue();
        assertThat(mTracker.checkIfSeen(userId, pattern)).isFalse();

        mTracker.clear(userId);
        mTracker.insert(userId, pattern);
        assertThat(mTracker.checkIfSeen(userId, password)).isFalse();
        assertThat(mTracker.checkIfSeen(userId, pin)).isFalse();
        assertThat(mTracker.checkIfSeen(userId, pattern)).isTrue();
    }

    private LockscreenCredential newPassword(String password) {
        return LockscreenCredential.createPasswordOrNone(password);
    }

    private LockscreenCredential newPin(String pin) {
        return LockscreenCredential.createPinOrNone(pin);
    }

    private LockscreenCredential newPattern(String pattern) {
        return LockscreenCredential.createPattern(LockPatternUtils.byteArrayToPattern(
                pattern.getBytes()));
    }

    private static class TestInjector extends WrongGuessTracker.Injector {

        public final ArrayList<ClearWork> clearWorkList = new ArrayList<>();

        @Override
        void cancelClearWork(Object token) {
            this.clearWorkList.removeIf(work -> work.token == token);
        }

        @Override
        void scheduleClearWork(Runnable runnable, Object token, long delayMillis) {
            this.clearWorkList.add(new ClearWork(runnable, token, delayMillis));
        }
    }

    private static class ClearWork {
        public final Runnable runnable;
        public final Object token;
        public final long delayMillis;

        ClearWork(Runnable runnable, Object token, long delayMillis) {
            this.runnable = runnable;
            this.token = token;
            this.delayMillis = delayMillis;
        }
    }
}
